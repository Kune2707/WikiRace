import { beforeEach, expect, it, vi } from "vitest";
import type { StompConfig } from "@stomp/stompjs";
import { connectRoom, isRoomEvent } from "./roomStream";
import { fixture } from "../lib/testFixtures";
const mocks = vi.hoisted(() => ({
  config: null as StompConfig | null,
  receipt: null as (() => void) | null,
  receive: null as ((message: { body: string }) => void) | null,
  activate: vi.fn(),
  deactivate: vi.fn(),
  subscribe: vi.fn(),
  watch: vi.fn(),
}));
vi.mock("@stomp/stompjs", () => ({
  ReconnectionTimeMode: { EXPONENTIAL: 1 },
  Client: class {
    constructor(config: StompConfig) {
      mocks.config = config;
    }
    activate = mocks.activate;
    deactivate = mocks.deactivate;
    watchForReceipt(_id: string, callback: () => void) {
      mocks.receipt = callback;
      mocks.watch();
    }
    subscribe(
      destination: string,
      receive: (message: { body: string }) => void,
      headers: unknown,
    ) {
      mocks.receive = receive;
      mocks.subscribe(destination, headers);
    }
  },
}));
beforeEach(() => {
  vi.clearAllMocks();
  mocks.config = null;
  mocks.receipt = null;
});
it("uses token headers only, subscribes to one room and waits for broker confirmation before REST resync", () => {
  const ready = vi.fn();
  const close = connectRoom(
    { code: "ABC234", token: "SECRET", playerId: "host" },
    vi.fn(),
    ready,
    vi.fn(),
  );
  expect(mocks.config?.brokerURL).not.toContain("SECRET");
  expect(mocks.config?.connectHeaders).toEqual({
    roomCode: "ABC234",
    "X-Player-Token": "SECRET",
  });
  mocks.config?.onConnect?.({} as never);
  expect(mocks.subscribe).toHaveBeenCalledWith(
    "/topic/rooms/ABC234",
    expect.objectContaining({ receipt: expect.any(String) }),
  );
  expect(ready).not.toHaveBeenCalled();
  mocks.receipt?.();
  expect(ready).toHaveBeenCalledOnce();
  close();
  expect(mocks.deactivate).toHaveBeenCalledOnce();
});
it("rejects other rooms, malformed versions and any unredacted close location", () => {
  const room = fixture().room;
  room.players[1].currentArticle = null;
  const event = {
    type: "PLAYER_MOVED",
    roomCode: "ABC234",
    version: 1,
    occurredAt: fixture().serverTime,
    room,
  };
  expect(isRoomEvent(event, "ABC234")).toBe(true);
  expect(isRoomEvent(event, "OTHER2")).toBe(false);
  expect(isRoomEvent({ ...event, version: 2 }, "ABC234")).toBe(false);
  room.players[1].currentArticle = "SECRET";
  expect(isRoomEvent(event, "ABC234")).toBe(false);
});
it("reports loss and deactivates authorization failures without logging tokens or sending gameplay", () => {
  const offline = vi.fn();
  const close = connectRoom(
    { code: "ABC234", token: "SECRET", playerId: "host" },
    vi.fn(),
    vi.fn(),
    offline,
  );
  mocks.config?.onWebSocketClose?.({} as never);
  expect(offline).toHaveBeenCalledWith(expect.stringContaining("Reconnecting"));
  mocks.config?.onStompError?.({} as never);
  expect(mocks.deactivate).toHaveBeenCalledOnce();
  expect(mocks.config?.debug).toBeDefined();
  expect(mocks.config?.logRawCommunication).toBe(false);
  close();
});
it("caps exponential reconnects and stops retrying cleaned rooms", () => {
  const offline = vi.fn();
  const close = connectRoom(
    { code: "ABC234", token: "SECRET", playerId: "host" },
    vi.fn(),
    vi.fn(),
    offline,
  );
  expect(mocks.config?.reconnectTimeMode).toBe(1);
  expect(mocks.config?.maxReconnectDelay).toBe(15000);
  mocks.config?.onWebSocketClose?.({ code: 1008 } as never);
  expect(offline).toHaveBeenCalledWith(expect.stringContaining("Room expired"));
  expect(mocks.deactivate).toHaveBeenCalledOnce();
  mocks.config?.onWebSocketClose?.({} as never);
  expect(offline).toHaveBeenCalledOnce();
  close();
});
it("pauses transport when offline, resumes online, and removes network listeners on cleanup", () => {
  const offline = vi.fn();
  const close = connectRoom(
    { code: "ABC234", token: "SECRET", playerId: "host" },
    vi.fn(),
    vi.fn(),
    offline,
  );
  window.dispatchEvent(new Event("offline"));
  expect(mocks.deactivate).toHaveBeenCalledWith({ force: true });
  expect(offline).toHaveBeenCalledWith(
    expect.stringContaining("Network offline"),
  );
  window.dispatchEvent(new Event("online"));
  expect(mocks.activate).toHaveBeenCalledTimes(2);
  close();
  window.dispatchEvent(new Event("online"));
  expect(mocks.activate).toHaveBeenCalledTimes(2);
});
it("ignores delayed receipts/events and disconnect callbacks after cleanup", () => {
  const ready = vi.fn(), receive = vi.fn(), offline = vi.fn();
  const close = connectRoom({code: "ABC234", token: "SECRET", playerId: "host"}, receive, ready, offline);
  mocks.config?.onConnect?.({} as never);
  close();
  mocks.receipt?.();
  mocks.receive?.({body: "malformed"});
  mocks.config?.onWebSocketClose?.({} as never);
  mocks.config?.onStompError?.({} as never);
  expect(ready).not.toHaveBeenCalled();
  expect(receive).not.toHaveBeenCalled();
  expect(offline).not.toHaveBeenCalled();
});
