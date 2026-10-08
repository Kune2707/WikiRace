import { expect, test } from "@playwright/test";
import { readFileSync } from "node:fs";
const fixtures: { title: string; revisionId: number; sourceUrl: string; html: string }[] = JSON.parse(readFileSync(new URL("./fixtures/wikipedia-tables.json", import.meta.url), "utf8"));

const widths = [1440, 1280, 1024, 768, 390, 320];
const stress = {
  title: "Unclassified table regression",
  revisionId: 1,
  sourceUrl: "https://en.wikipedia.org/",
  html: '<table><caption>Compact</caption><tr><th>A</th><td>B</td></tr></table>' +
    '<table><caption>Merged cells and notes</caption><thead><tr><th colspan="3">Awards and history</th></tr></thead><tbody><tr><th rowspan="3" scope="row">Career</th><td colspan="2">A detailed explanation of the award and its historical context. ' + 'Readable narrative text inside a cell. '.repeat(35) + '</td></tr><tr><td><ul><li>First nomination</li><li>Second nomination</li></ul></td><td><a href="#" data-wiki-title="Actor">Actor</a></td></tr><tr><td colspan="2">' + 'UnbrokenToken'.repeat(60) + '</td></tr></tbody></table>' +
    '<table class="wikitable sortable"><caption>Wide statistics</caption><tr>' + '<th>International nomination category</th>'.repeat(12) + '</tr><tr>' + '<td>Historical award winner</td>'.repeat(12) + '</tr></table>',
};

for (const [index, fixture] of [...fixtures, stress].entries()) {
  test(`global table rendering: ${fixture.title}`, async ({ page }) => {
    test.setTimeout(60000);
    await page.route("**/en.wikipedia.org/**", () => { throw new Error("Tests must not contact live Wikipedia"); });
    // Table fixtures are offline; mock the transport handshake, not gameplay commands.
    await page.routeWebSocket("**/ws", socket => socket.onMessage(message => {
      const frame = message.toString();
      if (frame.startsWith("CONNECT")) socket.send("CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0");
      const receipt = frame.match(/\nreceipt:([^\n]+)/)?.[1];
      if (receipt) socket.send(`RECEIPT\nreceipt-id:${receipt}\n\n\0`);
    }));
    const now = new Date().toISOString();
    const view = {
      serverTime: now,
      room: {
        roomCode: "TBL234", version: 2, status: "ACTIVE", hostPlayerId: "host",
        settings: { startArticle: fixture.title, targetArticle: "Actor", unlimited: true, timeLimitSeconds: null },
        players: [{ playerId: "host", displayName: "Table Tester", host: true, ready: true, connected: true, clickCount: 1, closeToTarget: false, currentArticle: fixture.title }],
        startsAt: now, finishedAt: null, suddenDeathStartedAt: null, winnerPlayerId: null,
      },
      me: { playerId: "host", currentArticle: fixture.title, clickCount: 1, canGoBack: false, movementRevision: 1 },
    };
    await page.addInitScript(() => sessionStorage.setItem("wikirace.session", JSON.stringify({ code: "TBL234", token: "offline-table-fixture" })));
    await page.route("**/api/rooms/TBL234", route => route.fulfill({ json: view }));
    await page.route("**/api/rooms/TBL234/me/article", route => route.fulfill({ json: { ...fixture, roomCode: "TBL234", playerId: "host", roomVersion: 2, movementRevision: 1 } }));
    await page.goto("/room/TBL234");
    await expect(page.locator(".connection-state")).toHaveText("Connected");
    await expect(page.locator(".wiki-content table").first()).toBeVisible();
    const originalSpans = await page.evaluate(html => [...new DOMParser().parseFromString(html, "text/html").querySelectorAll("td[rowspan],th[rowspan],td[colspan],th[colspan]")].map(el => [el.tagName, el.getAttribute("rowspan"), el.getAttribute("colspan")]), fixture.html);
    expect(await page.locator(".wiki-content td[rowspan],.wiki-content th[rowspan],.wiki-content td[colspan],.wiki-content th[colspan]").evaluateAll(els => els.map(el => [el.tagName, el.getAttribute("rowspan"), el.getAttribute("colspan")]))).toEqual(originalSpans);
    for (const width of widths) {
      await page.setViewportSize({ width, height: width <= 390 ? 844 : 1000 });
      const layout = await page.evaluate(() => {
        const article = document.querySelector(".article-scroll")!;
        const tables = [...document.querySelectorAll<HTMLTableElement>(".wiki-content table")];
        return {
          pageOverflow: document.documentElement.scrollWidth > innerWidth,
          articleOverflow: article.scrollWidth > article.clientWidth + 1,
          tables: tables.map(table => {
            const wrapper = table.parentElement!;
            const style = getComputedStyle(table);
            return { display: style.display, layout: style.tableLayout, collapse: style.borderCollapse, width: table.getBoundingClientRect().width, wrapperWidth: wrapper.clientWidth, overflow: wrapper.scrollWidth > wrapper.clientWidth + 1, focusable: wrapper.tabIndex === 0, label: wrapper.getAttribute("aria-label"), infobox: table.classList.contains("infobox") };
          }),
        };
      });
      expect(layout.pageOverflow).toBe(false);
      expect(layout.articleOverflow).toBe(false);
      for (const table of layout.tables) {
        expect(table.display).toBe("table");
        expect(table.layout).toBe("auto");
        expect(table.collapse).toBe("collapse");
        expect(table.focusable).toBe(true);
        expect(table.label).toBeTruthy();
        if (table.infobox) expect(table.width).toBeLessThanOrEqual(table.wrapperWidth + 1);
      }
      if (index === fixtures.length) {
        expect(layout.tables[0].width).toBeLessThan(layout.tables[0].wrapperWidth);
        expect(layout.tables[2].overflow).toBe(true);
        const scroller = page.getByRole("region", { name: "Wide statistics", exact: true });
        await scroller.focus();
        await page.keyboard.press("ArrowRight");
        await expect.poll(() => scroller.evaluate(el => el.scrollLeft)).toBeGreaterThan(0);
        await expect(page.getByRole("link", { name: "Actor", exact: true })).toHaveAttribute("data-wiki-title", "Actor");
      }
      await page.locator(".article-scroll").evaluate(el => { el.scrollTop = 0; });
      await page.screenshot({ path: `test-results/table-${index}-${width}.png`, animations: "disabled" });
      if (index === fixtures.length || index === 2) {
        await page.locator(".wiki-table-scroll").last().scrollIntoViewIfNeeded();
        await page.screenshot({ path: `test-results/table-wide-${index}-${width}.png`, animations: "disabled" });
      }
    }
  });
}
