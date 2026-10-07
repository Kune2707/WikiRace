import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { useRoom } from "./useRoom";
import { api, ApiError } from "../lib/api";
import { connectRoom } from "../realtime/roomStream";
import type { Identity, RoomEvent } from "../lib/types";
vi.mock("../realtime/roomStream", () => ({
  connectRoom: vi.fn(
    (_id: Identity, _event: (event: RoomEvent) => void, ready: () => void) => {
      queueMicrotask(ready);
      return vi.fn();
    },
  ),
}));
import { movement } from "../lib/game";
import { fixture } from "../lib/testFixtures";
import type { Article, View } from "../lib/types";
const id = { code: "ABC234", token: "private-token", playerId: "host" };
const article: Article = {
  roomCode: "ABC234",
  playerId: "host",
  roomVersion: 1,
  movementRevision: 0,
  title: "Computer Science",
  html: "<p>First</p>",
  sourceUrl: "https://en.wikipedia.org/wiki/Computer_science",
  revisionId: 42,
};
beforeEach(() => {
  vi.spyOn(api, "room").mockResolvedValue(fixture());
  vi.spyOn(api, "article").mockResolvedValue(article);
});
async function attached() {
  const hook = renderHook(useRoom);
  act(() => hook.result.current.attach(id, fixture()));
  await waitFor(() =>
    expect(hook.result.current.article?.title).toBe("Computer Science"),
  );
  return hook;
}
it("serializes clicks, makes no optimistic changes and applies confirmation once", async () => {
  const hook = await attached();
  let resolve!: (value: { view: View }) => void;
  const move = vi.spyOn(api, "move").mockImplementation(
    () =>
      new Promise((done) => {
        resolve = done;
      }),
  );
  const intent = movement("navigate", "Algorithm");
  act(() => {
    void hook.result.current.perform("", {}, intent);
    void hook.result.current.perform("", {}, intent);
  });
  expect(move).toHaveBeenCalledTimes(1);
  expect(hook.result.current.view?.me.clickCount).toBe(0);
  const next = fixture();
  next.room.version = 2;
  next.me.clickCount = 1;
  next.me.currentArticle = "Algorithm";
  next.me.movementRevision = 1;
  await act(async () => resolve({ view: next }));
  expect(hook.result.current.view?.me.clickCount).toBe(1);
  expect(hook.result.current.pending).toBeNull();
});
it("retains the same action UUID when delivery is uncertain", async () => {
  const hook = await attached();
  const move = vi
    .spyOn(api, "move")
    .mockRejectedValueOnce(new ApiError("NETWORK", "Offline", true))
    .mockResolvedValue({ view: fixture() });
  const intent = movement("back");
  await act(async () => {
    await hook.result.current.perform("", {}, intent);
  });
  expect(hook.result.current.pending?.actionId).toBe(intent.actionId);
  await act(async () => {
    await hook.result.current.perform(
      "",
      {},
      movement("navigate", "Mathematics"),
    );
  });
  expect(move).toHaveBeenCalledTimes(1);
  await act(async () => {
    await hook.result.current.perform("", {}, hook.result.current.pending!);
  });
  expect(move.mock.calls[0][1].actionId).toBe(move.mock.calls[1][1].actionId);
  expect(hook.result.current.pending).toBeNull();
});

it.each([
  "WAITING",
  "COUNTDOWN",
  "ACTIVE",
  "SUDDEN_DEATH",
  "FINISHED",
] as const)(
  "resumes %s from credentials, never cached gameplay or player identity",
  async (status) => {
    const restored = fixture(status);
    restored.me.playerId = "server-identity";
    restored.me.clickCount = 7;
    restored.me.movementRevision = 7;
    vi.mocked(api.room).mockResolvedValue(restored);
    const hook = renderHook(useRoom);
    await act(async () => {
      await hook.result.current.resume({ ...id, playerId: "forged-id" });
    });
    expect(hook.result.current.identity?.playerId).toBe("server-identity");
    expect(hook.result.current.view?.room.status).toBe(status);
    expect(hook.result.current.view?.me.clickCount).toBe(7);
    expect(
      JSON.parse(localStorage.getItem("wikirace.room." + id.code)!),
    ).toEqual({ code: id.code, token: id.token });
    expect(JSON.parse(sessionStorage.getItem("wikirace.session")!)).toEqual({
      code: id.code,
      token: id.token,
    });
  },
);
it("forgets expired credentials instead of creating a new player", async () => {
  vi.mocked(api.room).mockRejectedValue(
    new ApiError("ROOM_NOT_FOUND", "Room not found."),
  );
  localStorage.setItem("wikirace.room." + id.code, JSON.stringify(id));
  const enter = vi.spyOn(api, "enter");
  const hook = renderHook(useRoom);
  await act(async () => {
    await hook.result.current.resume(id);
  });
  expect(hook.result.current.identity).toBeNull();
  expect(localStorage.getItem("wikirace.room." + id.code)).toBeNull();
  expect(enter).not.toHaveBeenCalled();
});
it("does not revive a session when a delayed resume completes after leaving", async () => {
  let resolve!: (view: View) => void;
  vi.mocked(api.room).mockImplementation(
    () =>
      new Promise((done) => {
        resolve = done;
      }),
  );
  const hook = renderHook(useRoom);
  act(() => {
    void hook.result.current.resume(id);
  });
  act(() => hook.result.current.reset());
  await act(async () => resolve(fixture()));
  expect(hook.result.current.identity).toBeNull();
  expect(hook.result.current.view).toBeNull();
});
it("releases busy state when session restore is cancelled", async () => {
  let reject!: (error: Error) => void;
  vi.mocked(api.room).mockImplementation(() => new Promise((_resolve, fail) => { reject = fail; }));
  const hook = renderHook(useRoom);
  const controller = new AbortController();
  let restoring!: Promise<void>;
  act(() => { restoring = hook.result.current.resume(id, controller.signal); });
  expect(hook.result.current.busy).toBe(true);
  await act(async () => {
    controller.abort();
    reject(new DOMException("Cancelled", "AbortError"));
    await restoring;
  });
  expect(hook.result.current.busy).toBe(false);
  expect(hook.result.current.error).toBe("");
  expect(hook.result.current.identity).toBeNull();
});
it("only the newest overlapping restore can choose the tab identity", async () => {
  let older!: (view: View) => void, newer!: (view: View) => void;
  vi.mocked(api.room)
    .mockImplementationOnce(() => new Promise(done => { older = done; }))
    .mockImplementationOnce(() => new Promise(done => { newer = done; }));
  const hook = renderHook(useRoom);
  act(() => {
    void hook.result.current.resume(id);
    void hook.result.current.resume({...id, token: "new-token"});
  });
  await act(async () => older(fixture()));
  expect(hook.result.current.identity).toBeNull();
  expect(hook.result.current.busy).toBe(true);
  await act(async () => newer(fixture()));
  expect(hook.result.current.identity?.token).toBe("new-token");
  expect(hook.result.current.busy).toBe(false);
});
it("does not attach a delayed create response after leaving", async () => {
  let resolve!: (session: Awaited<ReturnType<typeof api.enter>>) => void;
  vi.spyOn(api, "enter").mockImplementation(() => new Promise(done => {resolve = done;}));
  const hook = renderHook(useRoom);
  act(() => { void hook.result.current.enter("Alex"); });
  act(() => hook.result.current.reset());
  await act(async () => resolve({playerId: "host", playerSessionToken: "old-token", view: fixture()}));
  expect(hook.result.current.identity).toBeNull();
  expect(hook.result.current.busy).toBe(false);
  expect(sessionStorage.getItem("wikirace.session")).toBeNull();
});
it("does not let restore supersede an in-flight create and strand its command lock", async () => {
  let resolve!: (session: Awaited<ReturnType<typeof api.enter>>) => void;
  vi.spyOn(api, "enter").mockImplementation(() => new Promise(done => {resolve = done;}));
  vi.mocked(api.room).mockClear();
  const hook = renderHook(useRoom);
  act(() => { void hook.result.current.enter("Alex"); });
  await act(async () => { await hook.result.current.resume(id); });
  expect(api.room).not.toHaveBeenCalled();
  expect(hook.result.current.busy).toBe(true);
  await act(async () => resolve({playerId: "host", playerSessionToken: "new-token", view: fixture()}));
  expect(hook.result.current.identity?.token).toBe("new-token");
  expect(hook.result.current.busy).toBe(false);
});
it("rejects stale snapshots and wrong-player article content", async () => {
  vi.mocked(api.article).mockResolvedValue({
    ...article,
    playerId: "guest",
    html: "SECRET",
  });
  const hook = renderHook(useRoom);
  act(() => hook.result.current.attach(id, fixture()));
  await waitFor(() =>
    expect(hook.result.current.articleError).toContain("location changed"),
  );
  expect(hook.result.current.article).toBeNull();
  const newer = fixture();
  newer.room.version = 4;
  newer.me.clickCount = 2;
  newer.me.movementRevision = 2;
  vi.mocked(api.room).mockResolvedValue(newer);
  await act(async () => hook.result.current.refresh());
  vi.mocked(api.room).mockResolvedValue(fixture());
  await act(async () => hook.result.current.refresh());
  expect(hook.result.current.view?.room.version).toBe(4);
  expect(hook.result.current.view?.me.clickCount).toBe(2);
});
it("clears rejected movements for a fresh retry without changing clicks", async () => {
  const hook = await attached();
  vi.spyOn(api, "move").mockRejectedValue(
    new ApiError("INVALID_NAVIGATION", "Not linked"),
  );
  await act(async () => {
    await hook.result.current.perform("", {}, movement("navigate", "Target"));
  });
  expect(hook.result.current.pending).toBeNull();
  expect(hook.result.current.view?.me.clickCount).toBe(0);
});

it("ignores an old command confirmation after the tab switches identity", async () => {
  const hook = await attached();
  let resolve!: (value: { view: View }) => void;
  vi.spyOn(api, "move").mockImplementation(
    () =>
      new Promise((done) => {
        resolve = done;
      }),
  );
  act(() => {
    void hook.result.current.perform("", {}, movement("navigate", "Algorithm"));
  });
  const other = fixture();
  other.me.playerId = "guest";
  vi.mocked(api.room).mockResolvedValue(other);
  act(() =>
    hook.result.current.attach(
      { ...id, playerId: "guest", token: "guest-token" },
      other,
    ),
  );
  const old = fixture();
  old.room.version = 10;
  old.me.clickCount = 1;
  await act(async () => resolve({ view: old }));
  expect(hook.result.current.view?.me.playerId).toBe("guest");
  expect(hook.result.current.view?.me.clickCount).toBe(0);
});

it("applies only newer shared events, preserves hidden own state and does not poll", async () => {
  const hook = await attached();
  const receive = vi.mocked(connectRoom).mock.calls.at(-1)![1];
  const count = vi.mocked(api.room).mock.calls.length;
  const event: RoomEvent = {
    type: "PLAYER_MOVED",
    roomCode: id.code,
    version: 3,
    occurredAt: fixture().serverTime,
    room: { ...fixture().room, version: 3 },
  };
  act(() => receive(event));
  expect(hook.result.current.view?.room.version).toBe(3);
  expect(hook.result.current.view?.me.currentArticle).toBe("Computer Science");
  act(() =>
    receive({ ...event, version: 2, room: { ...event.room, version: 2 } }),
  );
  expect(hook.result.current.view?.room.version).toBe(3);
  await new Promise((resolve) => setTimeout(resolve, 50));
  expect(vi.mocked(api.room).mock.calls.length).toBe(count);
});

it("refreshes private state after own movement even if a newer shared event beats the REST reply", async () => {
  const hook = await attached();
  const receive = vi.mocked(connectRoom).mock.calls.at(-1)![1];
  const privateView = fixture();
  privateView.room.version = 2;
  privateView.me.currentArticle = "Mathematics";
  privateView.me.movementRevision = 1;
  privateView.me.clickCount = 1;
  vi.mocked(api.room).mockResolvedValue(privateView);
  const room = structuredClone(fixture().room);
  room.version = 3;
  room.players[0].clickCount = 1;
  act(() =>
    receive({
      type: "PLAYER_CLOSE_CHANGED",
      roomCode: id.code,
      version: 3,
      occurredAt: fixture().serverTime,
      room,
    }),
  );
  await waitFor(() =>
    expect(hook.result.current.view?.me.currentArticle).toBe("Mathematics"),
  );
  expect(hook.result.current.view?.room.version).toBe(3);
  expect(hook.result.current.view?.room.players[0].currentArticle).toBeNull();
});
