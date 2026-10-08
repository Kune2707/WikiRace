import { expect, it } from "vitest";
import { safeArticle } from "./article";
it("preserves merged cells and header relationships inside keyboard-accessible scroll regions", () => {
  const container = document.createElement("div");
  container.innerHTML = safeArticle('<table class="infobox wikitable sortable evil" style="width:9999px;float:right;position:fixed;display:block" width="9999"><caption>Credits</caption><colgroup span="2"><col span="2"></colgroup><thead><tr><th id="wiki-name" scope="col" colspan="2">Actor</th></tr></thead><tbody><tr><th scope="row" rowspan="2">Burton</th><td headers="wiki-name" rowspan="0"><ul><li><a href="#" data-wiki-title="Actor">Actor</a></li></ul></td></tr><tr></tr></tbody><tfoot><tr><td colspan="2">Notes</td></tr></tfoot></table>');
  const table = container.querySelector("table")!;
  expect(table.className).toBe("infobox wikitable sortable");
  expect(container.querySelector("[style],[width]")).toBeNull();
  expect(table.querySelector("th")!.getAttribute("colspan")).toBe("2");
  expect(table.querySelector("th[scope=row]")!.getAttribute("rowspan")).toBe("2");
  expect(table.querySelector("td")!.getAttribute("rowspan")).toBe("0");
  expect(table.querySelector("td")!.getAttribute("headers")).toBe("wiki-name");
  expect(table.querySelectorAll("caption,thead,tbody,tfoot,colgroup,col,ul,li,a[data-wiki-title]")).toHaveLength(9);
  expect(table.parentElement!.tabIndex).toBe(0);
  expect(table.parentElement!.getAttribute("role")).toBe("region");
  expect(table.parentElement!.getAttribute("aria-label")).toBe("Credits");
});
it("wraps unknown and nested tables without leaking arbitrary CSS classes", () => {
  const container = document.createElement("div");
  container.innerHTML = safeArticle('<table class="application"><tr><td class="race-grid" colspan="2"><table><tr><td>Nested</td></tr></table></td></tr></table>');
  expect(container.querySelectorAll(".wiki-table-scroll")).toHaveLength(2);
  expect(container.querySelector(".application,.race-grid")).toBeNull();
  expect(container.querySelector("td")!.getAttribute("colspan")).toBe("2");
});
it("preserves block separation inside cells and strips all inline layout styles", () => {
  const container = document.createElement("div");
  container.innerHTML = safeArticle('<div>Outside</div><table><tr><td><div style="float:right;width:1px;min-width:999px;max-width:1px;clear:both;display:none;position:fixed">First entry</div><div>Second entry</div><div>\u200b</div><ul><li><style>bad</style></li><li>Meaningful item</li></ul></td></tr></table>');
  expect(container.querySelectorAll("td > div")).toHaveLength(2);
  expect(container.firstChild?.textContent).toBe("Outside");
  expect(container.querySelector("[style]")).toBeNull();
  expect(container.querySelector("li:last-child")?.textContent).toBe("Meaningful item");
});
it("removes active content, images, forged external links and event handlers", () => {
  const html = safeArticle(
    '<script>bad()</script><p onclick="bad()">Safe <b>text</b></p><iframe></iframe><img src="x" onerror="bad()"><form><input></form><a href="https://evil.test" data-wiki-title="Target">External</a><a href="javascript:bad()">Unsafe</a>',
  );
  expect(html).toContain("<b>text</b>");
  expect(html).toContain("External");
  for (const unsafe of [
    "<script",
    "onclick",
    "<iframe",
    "<img",
    "<form",
    "<input",
    "evil.test",
    "javascript:",
    "data-wiki-title",
  ])
    expect(html).not.toContain(unsafe);
});
it("preserves only controlled movement links and local section anchors", () => {
  const html = safeArticle(
    '<a href="#" data-wiki-title="Graph theory">Graph</a><h2 id="wiki-Intro">Intro</h2><a href="#wiki-Intro">Section</a>',
  );
  expect(html).toContain('data-wiki-title="Graph theory"');
  expect(html).toContain('href="#wiki-Intro"');
});
