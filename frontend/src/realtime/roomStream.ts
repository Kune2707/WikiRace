import { Client, ReconnectionTimeMode } from "@stomp/stompjs";
import { apiBase, isView } from "../lib/api";
import type { Identity, RoomEvent } from "../lib/types";

const eventTypes = [
  "ROOM_UPDATED",
  "PLAYER_JOINED",
  "PLAYER_READY_CHANGED",
  "SETTINGS_UPDATED",
  "COUNTDOWN_STARTED",
  "RACE_STARTED",
  "PLAYER_MOVED",
  "PLAYER_CLOSE_CHANGED",
  "PLAYER_DISCONNECTED",
  "PLAYER_RECONNECTED",
  "SUDDEN_DEATH_STARTED",
  "RACE_FINISHED",
];
export function isRoomEvent(value: unknown, code: string): value is RoomEvent {
  if (
    typeof value !== "object" ||
    value === null ||
    !("room" in value) ||
    !("version" in value) ||
    !("roomCode" in value) ||
    !("type" in value) ||
    !("occurredAt" in value)
  )
    return false;
  const room = value.room;
  if (
    typeof room !== "object" ||
    room === null ||
    !("players" in room) ||
    !Array.isArray(room.players) ||
    !room.players.length
  )
    return false;
  const first = room.players[0];
  if (typeof first !== "object" || first === null || !("playerId" in first))
    return false;
  return (
    value.roomCode === code &&
    typeof value.version === "number" &&
    Number.isSafeInteger(value.version) &&
    value.version >= 0 &&
    typeof value.type === "string" &&
    eventTypes.includes(value.type) &&
    typeof value.occurredAt === "string" &&
    isView({
      serverTime: value.occurredAt,
      room,
      me: {
        playerId: first.playerId,
        currentArticle: null,
        clickCount: 0,
        canGoBack: false,
        movementRevision: 0,
      },
    }) &&
    "version" in room &&
    room.version === value.version &&
    "roomCode" in room &&
    room.roomCode === code &&
    !("me" in value) &&
    !("playerSessionToken" in value) &&
    room.players.every((p) => !p.closeToTarget || p.currentArticle === null)
  );
}

export function connectRoom(
  id: Identity,
  onEvent: (event: RoomEvent) => void,
  ready: () => void,
  offline: (message: string) => void,
) {
  const url = new URL(apiBase);
  url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
  url.pathname = url.pathname.replace(/\/$/, "") + "/ws";
  url.search = "";
  url.hash = "";
  let stopped = false;
  let terminal = false;
  const client = new Client({
    brokerURL: url.toString(),
    connectHeaders: { roomCode: id.code, "X-Player-Token": id.token },
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    reconnectDelay: 1500,
    reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
    maxReconnectDelay: 15000,
    connectionTimeout: 10000,
    beforeConnect: () =>
      new Promise<void>((resolve) =>
        window.setTimeout(resolve, Math.floor(Math.random() * 300)),
      ),
    debug: () => {},
    logRawCommunication: false,
    onConnect: () => {
      if (stopped) return;
      const receipt = crypto.randomUUID();
      client.watchForReceipt(receipt, () => {
        if (!stopped) ready();
      });
      client.subscribe(
        "/topic/rooms/" + id.code,
        (message) => {
          if (stopped) return;
          try {
            const value: unknown = JSON.parse(message.body);
            if (isRoomEvent(value, id.code)) onEvent(value);
          } catch {
            offline("Invalid room event. Refresh room state.");
          }
        },
        { receipt },
      );
    },
    onWebSocketClose: (event) => {
      if (event.code === 1008 && !stopped) {
        terminal = true;
        offline("Room expired. Return home to create or join a room.");
        void client.deactivate();
        return;
      }
      if (terminal) return;
      if (!stopped) offline("Real-time connection lost. Reconnecting...");
    },
    onStompError: () => {
      if (!stopped) {
        terminal = true;
        offline(
          "Real-time authorization failed. Resume your session or return home.",
        );
        void client.deactivate();
      }
    },
  });
  const networkOffline = () => {
    if (stopped || terminal) return;
    offline("Network offline. Waiting for connection...");
    void client.deactivate({ force: true });
  };
  const networkOnline = () => {
    if (!stopped && !terminal) client.activate();
  };
  window.addEventListener("offline", networkOffline);
  window.addEventListener("online", networkOnline);
  if (navigator.onLine) client.activate();
  else networkOffline();
  return () => {
    stopped = true;
    window.removeEventListener("offline", networkOffline);
    window.removeEventListener("online", networkOnline);
    void client.deactivate();
  };
}
