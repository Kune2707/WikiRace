import { expect, test } from "@playwright/test";

const viewports = [
  { width: 1440, height: 900 },
  { width: 1280, height: 800 },
  { width: 1024, height: 768 },
  { width: 768, height: 1024 },
  { width: 390, height: 844 },
  { width: 320, height: 568 },
];

test("two tabs complete the single-browser REST race with countdown, Back and winner", async ({
  page,
  context,
}) => {
  test.setTimeout(90000);
  await page.goto("/");
  await expect(page.getByText("Backend connected")).toBeVisible();
  await page.getByLabel("Display name").fill("Alex");
  await page.getByRole("button", { name: "Create Room" }).click();
  await expect(
    page.getByRole("heading", { name: /Room [A-Z2-9]{6}/ }),
  ).toBeVisible();
  const code = (
    await page.getByRole("heading", { name: /Room / }).innerText()
  ).split(" ")[1];
  await page.getByRole("combobox", { name: "Start article" }).fill("Computer");
  await page.getByRole("option", { name: "Computer Science" }).click();
  await page.getByRole("combobox", { name: "Target article" }).fill("Quantum");
  await page.getByRole("option", { name: "Quantum Mechanics" }).click();
  await page.getByRole("button", { name: "Save Settings" }).click();
  await expect(page.getByText("Race settings saved.")).toBeVisible();
  const hostId = await page.evaluate(() =>
    JSON.parse(sessionStorage.getItem("wikirace.session")!),
  );
  expect(Object.keys(hostId).sort()).toEqual(["code", "token"]);
  await page.reload();
  await expect(
    page.getByRole("heading", { name: "Room " + code }),
  ).toBeVisible();
  await expect(page.locator(".connection-state")).toHaveText("Connected");
  const guest = await context.newPage();
  await guest.goto("/room/" + code);
  await guest.getByLabel("Display name").fill("Sarah");
  await guest.getByRole("button", { name: "Join Room" }).click();
  await expect(
    guest.getByRole("heading", { name: "Room " + code }),
  ).toBeVisible();
  await guest.getByRole("button", { name: "Ready", exact: true }).click();
  await page.getByRole("button", { name: "Ready", exact: true }).click();
  await expect(page.getByRole("button", { name: "Start Race" })).toBeEnabled();
  for (const viewport of viewports) {
    await page.setViewportSize(viewport);
    await expect(page.getByRole("button", { name: "Start Race" })).toBeInViewport();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: `test-results/lobby-${viewport.width}.png`, animations: "disabled" });
  }
  await page.setViewportSize(viewports[0]);
  await page.screenshot({ path: "test-results/lobby-desktop.png", animations: "disabled" });
  await page.getByRole("button", { name: "Start Race" }).click();
  await expect(page.getByText("GET READY")).toBeVisible();
  await page.reload();
  await expect(page.getByText("GET READY")).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Algorithm", exact: true }),
  ).toBeVisible({ timeout: 8000 });
  await expect(page.getByText("GO", { exact: true })).not.toBeVisible({
    timeout: 3000,
  });
  await page.getByRole("link", { name: "Algorithm", exact: true }).click();
  await expect(page.locator(".article-heading h1")).toHaveText("Algorithm");
  await expect(page.locator(".click-counter strong")).toHaveText("1");
  await page.reload();
  await expect(page.locator(".article-heading h1")).toHaveText("Algorithm");
  await expect(page.locator(".click-counter strong")).toHaveText("1");
  await expect(page.locator(".connection-state")).toHaveText("Connected");
  await page.evaluate(() => history.back());
  await expect(page.locator(".article-heading h1")).toHaveText(
    "Computer Science",
  );
  await expect(page.locator(".click-counter strong")).toHaveText("2");
  await page.evaluate(() => history.back());
  await page.waitForTimeout(300);
  await expect(page.locator(".click-counter strong")).toHaveText("2");
  await expect(page).toHaveURL(new RegExp("/room/" + code + "$"));
  await context.setOffline(true);
  await expect(page.locator(".connection-state")).toHaveText(
    "Backend unavailable",
    { timeout: 5000 },
  );
  await context.setOffline(false);
  await expect(page.locator(".connection-state")).toHaveText("Connected", {
    timeout: 25000,
  });
  await expect(page.locator(".article-heading h1")).toHaveText(
    "Computer Science",
  );
  await page.getByRole("link", { name: "Mathematics", exact: true }).click();
  await expect(page.locator(".article-heading h1")).toHaveText("Mathematics");
  await expect(guest.getByText("Alex is one click away!")).toBeVisible();
  await expect(guest.locator(".player.close")).toHaveCSS("background-color", "rgb(255, 240, 241)");
  await expect(guest.locator(".close-pressure")).toBeVisible();
  await guest.screenshot({path: "test-results/close-alert-desktop.png", animations: "disabled"});
  await expect(page.getByText("Alex is one click away!")).not.toBeVisible();
  await expect(guest.locator(".players")).not.toContainText("Mathematics");
  for (const viewport of viewports) {
    await page.setViewportSize(viewport);
    await expect(page.locator(".header-room")).toContainText(code);
    await expect(page.locator(".target")).toContainText("Quantum Mechanics");
    await expect(page.getByRole("button", { name: "Back", exact: true })).toBeInViewport();
    const bounds = await page.evaluate(() => {
      const article = document.querySelector(".article-scroll")!.getBoundingClientRect();
      const players = document.querySelector(".players")!.getBoundingClientRect();
      return { overflow: document.documentElement.scrollWidth > innerWidth, article: { x: article.x, right: article.right, bottom: article.bottom, height: article.height }, players: { x: players.x, right: players.right, top: players.top, bottom: players.bottom }, height: innerHeight };
    });
    expect(bounds.overflow).toBe(false);
    expect(bounds.article.height).toBeGreaterThan(100);
    expect(bounds.players.bottom).toBeLessThanOrEqual(bounds.height + 1);
    if (viewport.width > 600) expect(bounds.article.right).toBeLessThanOrEqual(bounds.players.x + 1);
    else expect(bounds.article.bottom).toBeLessThanOrEqual(bounds.players.top + 1);
    await page.screenshot({ path: `test-results/fullscreen-race-${viewport.width}.png`, animations: "disabled" });
  }
  await page.screenshot({ path: "test-results/race-desktop.png", animations: "disabled" });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(
    page.getByRole("button", { name: "Back", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Quantum Mechanics", exact: true }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await page.screenshot({ path: "test-results/race-mobile.png", animations: "disabled" });
  await page
    .getByRole("link", { name: "Quantum Mechanics", exact: true })
    .click();
  await expect(page.getByRole("heading", { name: "Alex wins" })).toBeVisible();
  await expect(page.locator(".click-counter strong")).toHaveText("4");
  await expect(guest.getByRole("heading", { name: "Alex wins" })).toBeVisible();
  await expect(page.locator(".race-won")).toBeVisible();
  await expect(guest.locator(".race-lost")).toBeVisible();
  await guest.screenshot({path: "test-results/loss-desktop.png", animations: "disabled"});
  await page.reload();
  await expect(page.getByRole("heading", { name: "Alex wins" })).toBeVisible();
  await expect(page.locator(".click-counter strong")).toHaveText("4");
  const saved = await page.evaluate(() =>
    JSON.parse(sessionStorage.getItem("wikirace.session")!),
  );
  expect(saved).toEqual(hostId);
  await page.screenshot({ path: "test-results/results-desktop.png", animations: "disabled" });
  for (const viewport of viewports) {
    await page.setViewportSize(viewport);
    await expect(page.getByRole("heading", { name: "Alex wins" })).toBeInViewport();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: `test-results/fullscreen-results-${viewport.width}.png`, animations: "disabled" });
  }
  await guest.close();
});

test("full-screen layout fits six viewports, stays keyboard-accessible and reduced-motion safe", async ({
  page,
}) => {
  for (const viewport of viewports) {
    await page.setViewportSize(viewport);
    await page.goto("/");
    await expect(
      page.getByRole("button", { name: "Create Room" }),
    ).toBeVisible();
    await expect(page.getByRole("button", { name: "Join Room" })).toBeVisible();
    const geometry = await page.evaluate(() => {
      const screen = document.querySelector(".application")!.getBoundingClientRect();
      const button = [...document.querySelectorAll("button")]
        .find((b) => b.textContent?.includes("Join Room"))!
        .getBoundingClientRect();
      return {
        width: screen.width,
        screenBottom: screen.bottom,
        buttonBottom: button.bottom,
        overflow: document.documentElement.scrollWidth > innerWidth,
        height: innerHeight,
      };
    });
    expect(geometry.overflow).toBe(false);
    expect(geometry.screenBottom).toBeLessThanOrEqual(geometry.height);
    expect(geometry.buttonBottom).toBeLessThanOrEqual(geometry.screenBottom);
    expect(geometry.width).toBe(viewport.width);
    await expect(page.locator(".laptop, .keyboard, .desk-surface, .coffee-cup, .notebook")).toHaveCount(0);
    await page.screenshot({
      path: `test-results/landing-${viewport.width}.png`,
      animations: "disabled",
    });
  }
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.mouse.move(20, 20);
  expect(
    await page
      .locator(".game-viewport")
      .evaluate((el) => getComputedStyle(el).transform),
  ).toBe("none");
  await expect(page.locator(".landing")).toHaveCSS("animation-name", "none");
  await page.getByLabel("Display name").focus();
  await page.keyboard.type("Alex");
  await page.keyboard.press("Tab");
  await expect(page.getByRole("button", { name: "Create Room" })).toBeFocused();
});

test("mocked server timestamps show sudden death without waiting three minutes or changing backend rules", async ({
  page,
}) => {
  const serverTime = new Date().toISOString();
  const started = new Date(Date.now() - 188000).toISOString();
  const sudden = new Date(Date.now() - 8000).toISOString();
  const view = {
    serverTime,
    room: {
      roomCode: "ABC234",
      version: 2,
      status: "SUDDEN_DEATH",
      hostPlayerId: "host",
      settings: {
        startArticle: "Computer Science",
        targetArticle: "Quantum Mechanics",
        unlimited: false,
        timeLimitSeconds: 180,
      },
      players: [
        {
          playerId: "host",
          displayName: "Alex",
          host: true,
          ready: true,
          connected: true,
          clickCount: 3,
          closeToTarget: false,
          currentArticle: "Computer Science",
        },
      ],
      startsAt: started,
      suddenDeathStartedAt: sudden,
      finishedAt: null,
      winnerPlayerId: null,
    },
    me: {
      playerId: "host",
      currentArticle: "Computer Science",
      clickCount: 3,
      canGoBack: true,
      movementRevision: 1,
    },
  };
  await page.addInitScript(() =>
    sessionStorage.setItem(
      "wikirace.session",
      JSON.stringify({
        code: "ABC234",
        token: "offline-token",
        playerId: "host",
      }),
    ),
  );
  await page.route("**/api/rooms/ABC234", (route) =>
    route.fulfill({ json: view }),
  );
  await page.route("**/api/rooms/ABC234/me/article", (route) =>
    route.fulfill({
      json: {
        roomCode: "ABC234",
        playerId: "host",
        roomVersion: 2,
        movementRevision: 1,
        title: "Computer Science",
        html: "<p>Computer science</p>" + "<p>Readable long article content with a legitimate internal <a href=\"#\" data-wiki-title=\"Algorithm\">Algorithm</a> link.</p>".repeat(250) + "<table><tr><td>" + "LongUnbrokenArticleText".repeat(30) + "</td></tr></table>",
        sourceUrl: "https://en.wikipedia.org/wiki/Computer_science",
        revisionId: 42,
      },
    }),
  );
  await page.goto("/room/ABC234");
  await expect(page.locator(".sudden-banner")).toHaveText("SUDDEN DEATH");
  await expect(page.locator(".race-time strong")).toHaveText(/^\+00:0[89]$/);
  await page.reload();
  await expect(page.locator(".sudden-banner")).toHaveText("SUDDEN DEATH");
  await expect(page.locator(".click-counter strong")).toHaveText("3");
  expect(await page.locator(".article-scroll").evaluate(el => el.scrollHeight > el.clientHeight)).toBe(true);
  await page.locator(".article-scroll").evaluate(el => {el.scrollTop = el.scrollHeight;});
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.emulateMedia({reducedMotion: "reduce"});
  await expect(page.locator(".wiki-content")).toHaveCSS("animation-name", "none");
  await page.screenshot({ path: "test-results/sudden-death.png", animations: "disabled" });
});

test("entry errors remain actionable and fit the smallest mobile screen", async ({page}) => {
  await page.setViewportSize({width: 320, height: 568});
  for (const [code, message] of [["ROOM_FULL", "This room is full"], ["ROOM_NOT_FOUND", "Room not found or expired"], ["INVALID_RACE_STATE", "current race state"]]) {
    await page.route("**/api/rooms/ABC234/join", route => route.fulfill({status: 409, json: {code, message: "Backend rejection"}}));
    await page.goto("/");
    await page.getByLabel("Display name").fill("Alex");
    await page.getByLabel("Room code").fill("ABC234");
    await page.getByRole("button", {name: "Join Room"}).click();
    await expect(page.getByRole("alert")).toContainText(message);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await expect(page.getByRole("button", {name: "Join Room"})).toBeEnabled();
    await page.unroute("**/api/rooms/ABC234/join");
  }
  await page.screenshot({path: "test-results/error-mobile.png", animations: "disabled"});
});

test("real REST rejects forged state, ambiguous JSON and foreign room credentials", async ({request}) => {
  const base = "http://127.0.0.1:8082/api/rooms";
  const created = await request.post(base, {data: {displayName: "Audit Host"}});
  expect(created.status()).toBe(201);
  const host = await created.json();
  const room = `${base}/${host.view.room.roomCode}`;
  const headers = {"X-Player-Token": host.playerSessionToken, "Content-Type": "application/json"};
  for (const body of ['{"unlimited":true} {}', '{"unlimited":true,"unlimited":false}', '{"clickCount":999,"winnerPlayerId":"host"}']) {
    const response = await request.patch(room + "/settings", {headers, data: body});
    expect(response.status()).toBe(400);
    expect((await response.json()).code).toBe("INVALID_REQUEST");
  }
  const other = await (await request.post(base, {data: {displayName: "Other Host"}})).json();
  const denied = await request.get(room, {headers: {"X-Player-Token": other.playerSessionToken}});
  expect(denied.status()).toBe(401);
  expect((await denied.json()).code).toBe("INVALID_PLAYER_TOKEN");
  const current = await (await request.get(room, {headers})).json();
  expect(current.room).toEqual(host.view.room);
  expect(current.me.clickCount).toBe(0);
  expect(JSON.stringify(current)).not.toContain(host.playerSessionToken);
});
