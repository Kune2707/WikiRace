import { expect, it, vi } from "vitest";
import { api, ApiError, isView } from "./api";
import { fixture } from "./testFixtures";
it("sends tokens only in headers and movement intents only in the body", async () => {
  const fetch = vi
    .fn()
    .mockResolvedValue(
      new Response(JSON.stringify({ view: fixture() }), { status: 200 }),
    );
  vi.stubGlobal("fetch", fetch);
  await api.move(
    { code: "ABC234", token: "SECRET", playerId: "host" },
    { type: "back", actionId: "retained-id" },
  );
  const [url, options] = fetch.mock.calls[0];
  expect(url).not.toContain("SECRET");
  expect(options.headers["X-Player-Token"]).toBe("SECRET");
  expect(JSON.parse(options.body)).toEqual({ actionId: "retained-id" });
});
it("fails closed on malformed success data and preserves uncertain delivery", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(new Response("{}", { status: 200 })),
  );
  await expect(
    api.room({ code: "ABC234", token: "secret", playerId: "host" }),
  ).rejects.toMatchObject({ uncertain: true, code: "INVALID_RESPONSE" });
  expect(isView({ ...fixture(), me: { currentArticle: "Target" } })).toBe(
    false,
  );
});
it("maps upstream structured errors without considering rejection an accepted move", async () => {
  vi.stubGlobal(
    "fetch",
    vi
      .fn()
      .mockResolvedValue(
        new Response(
          JSON.stringify({
            code: "WIKIPEDIA_UNAVAILABLE",
            message: "Retry later",
          }),
          { status: 503 },
        ),
      ),
  );
  await expect(api.search("Graph")).rejects.toEqual(
    new ApiError("WIKIPEDIA_UNAVAILABLE", "Wikipedia is temporarily unavailable. Please retry in a moment."),
  );
});
it.each([
  ["ROOM_FULL", "This room is full"],
  ["ROOM_NOT_FOUND", "Room not found or expired"],
  ["INVALID_RACE_STATE", "current race state"],
  ["INVALID_NAVIGATION", "Your position has not changed"],
  ["RATE_LIMITED", "Pause briefly"],
  ["RACE_FINISHED", "already finished"],
])("makes %s actionable without changing rejection semantics", async (code, message) => {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({code, message: "raw"}), {status: 409})));
  await expect(api.search("Graph")).rejects.toMatchObject({code, uncertain: false, message: expect.stringContaining(message)});
});
