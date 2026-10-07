import { describe, expect, it } from "vitest";
import {
  formatTime,
  movement,
  newerView,
  normalizeCode,
  opponentLocation,
  timer,
  validCode,
  validName,
} from "./game";
import { fixture } from "./testFixtures";

describe("authoritative presentation helpers", () => {
  it.each([
    [0, "00:00"],
    [8, "00:08"],
    [65, "01:05"],
    [3600, "60:00"],
    [-9, "00:00"],
  ])("formats %s", (seconds, text) =>
    expect(formatTime(Number(seconds))).toBe(text),
  );
  it.each(["Alex", "Élodie", "王小明", "Jane Doe", "A._'-"])(
    "accepts display name %s",
    (name) => expect(validName(name)).toBe(true),
  );
  it.each(["A", "<Alex>", "a".repeat(21), "Alex!", "😀😀"])(
    "rejects display name %s",
    (name) => expect(validName(name)).toBe(false),
  );
  it("normalizes and validates room codes", () => {
    expect(normalizeCode(" abc234 ")).toBe("ABC234");
    expect(validCode(" abc234 ")).toBe(true);
    for (const code of ["AB0123", "ABCI23", "ABCDE", "ABC2345", "<code>"])
      expect(validCode(code)).toBe(false);
  });
  it("creates new UUID intents without writable gameplay state", () => {
    const a = movement("navigate", "Algorithm");
    const b = movement("navigate", "Algorithm");
    expect(a.actionId).toMatch(/^[a-f\d-]{36}$/);
    expect(a.actionId).not.toBe(b.actionId);
    expect(Object.keys(a)).toEqual(["actionId", "type", "destinationArticle"]);
    expect(movement("back")).not.toHaveProperty("destinationArticle");
    expect(() => movement("navigate")).toThrow();
  });
  it("shows zero without locally transitioning into sudden death", () => {
    const view = fixture();
    expect(timer(view, Date.parse(view.room.startsAt!) + 190000)).toBe("00:00");
    expect(view.room.status).toBe("ACTIVE");
    view.room.status = "SUDDEN_DEATH";
    view.room.suddenDeathStartedAt = "2026-10-07T00:03:00Z";
    expect(timer(view, Date.parse(view.room.startsAt!) + 188000)).toBe(
      "+00:08",
    );
  });
  it("derives unlimited and final times from server timestamps", () => {
    const view = fixture();
    view.room.settings.unlimited = true;
    expect(timer(view, Date.parse(view.room.startsAt!) + 65000)).toBe("01:05");
    view.room.status = "FINISHED";
    view.room.finishedAt = "2026-10-07T00:00:42Z";
    expect(timer(view, Date.parse(view.room.startsAt!) + 999000)).toBe("00:42");
  });
  it("never renders a close opponent article even if a response includes it", () =>
    expect(opponentLocation(fixture().room.players[1])).toBe("CLOSE!"));
  it("rejects stale room versions, private revisions and other identities", () => {
    const current = fixture();
    const incoming = structuredClone(current);
    incoming.room.version = 0;
    expect(newerView(current, incoming)).toBe(false);
    incoming.room.version = 2;
    incoming.me.movementRevision = 1;
    expect(newerView(current, incoming)).toBe(true);
    current.me.movementRevision = 2;
    expect(newerView(current, incoming)).toBe(false);
    incoming.me.playerId = "other";
    expect(newerView(current, incoming)).toBe(false);
  });
});
