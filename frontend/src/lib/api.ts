import type { Article, Identity, Intent, Session, View } from "./types";

export const apiBase = (
  import.meta.env.VITE_API_BASE_URL?.trim() || "http://localhost:8080"
).replace(/\/+$/, "");
export class ApiError extends Error {
  constructor(
    public code: string,
    message: string,
    public uncertain = false,
  ) {
    super(message);
  }
}
const errorMessages: Record<string, string> = {
  ROOM_FULL: "This room is full (4 players). Join another room or create your own.",
  ROOM_NOT_FOUND: "Room not found or expired. Check the room code or create a new room.",
  INVALID_RACE_STATE: "This action is unavailable in the current race state. Refresh the room to see its latest status.",
  INVALID_NAVIGATION: "That article is not a valid link from your current page. Your position has not changed.",
  RATE_LIMITED: "Too many moves at once. Pause briefly, then try again.",
  WIKIPEDIA_UNAVAILABLE: "Wikipedia is temporarily unavailable. Please retry in a moment.",
  RACE_FINISHED: "This race has already finished. No further moves can be made.",
};
const object = (value: unknown): value is Record<string, unknown> =>
  typeof value === "object" && value !== null;
const date = (value: unknown) =>
  typeof value === "string" && Number.isFinite(Date.parse(value));
const nullableText = (value: unknown) =>
  value === null || typeof value === "string";
const count = (value: unknown) =>
  typeof value === "number" && Number.isSafeInteger(value) && value >= 0;
export function isView(value: unknown): value is View {
  if (
    !object(value) ||
    !date(value.serverTime) ||
    !object(value.room) ||
    !object(value.me)
  )
    return false;
  const { room, me } = value;
  if (
    typeof room.roomCode !== "string" ||
    !count(room.version) ||
    !["WAITING", "COUNTDOWN", "ACTIVE", "SUDDEN_DEATH", "FINISHED"].includes(
      String(room.status),
    ) ||
    !object(room.settings) ||
    !Array.isArray(room.players) ||
    typeof room.hostPlayerId !== "string" ||
    typeof me.playerId !== "string" ||
    !nullableText(me.currentArticle) ||
    !count(me.clickCount) ||
    !count(me.movementRevision) ||
    typeof me.canGoBack !== "boolean"
  )
    return false;
  const settings = room.settings;
  return (
    nullableText(settings.startArticle) &&
    nullableText(settings.targetArticle) &&
    typeof settings.unlimited === "boolean" &&
    (settings.timeLimitSeconds === null || count(settings.timeLimitSeconds)) &&
    [room.startsAt, room.suddenDeathStartedAt, room.finishedAt].every(
      (v) => v === null || date(v),
    ) &&
    nullableText(room.winnerPlayerId) &&
    room.players.length >= 1 &&
    room.players.length <= 4 &&
    room.players.every(
      (p) =>
        object(p) &&
        typeof p.playerId === "string" &&
        typeof p.displayName === "string" &&
        ["host", "ready", "connected", "closeToTarget"].every(
          (key) => typeof p[key] === "boolean",
        ) &&
        count(p.clickCount) &&
        nullableText(p.currentArticle),
    ) &&
    room.players.some((p) => p.playerId === me.playerId) &&
    room.players.some((p) => p.playerId === room.hostPlayerId)
  );
}
function isSession(value: unknown): value is Session {
  return (
    object(value) &&
    typeof value.playerId === "string" &&
    typeof value.playerSessionToken === "string" &&
    isView(value.view) &&
    value.playerId === value.view.me.playerId
  );
}
function isArticle(value: unknown): value is Article {
  return (
    object(value) &&
    ["roomCode", "playerId", "title", "html", "sourceUrl"].every(
      (key) => typeof value[key] === "string",
    ) &&
    count(value.movementRevision) &&
    count(value.roomVersion) &&
    (value.revisionId === null || count(value.revisionId))
  );
}
export async function request<T>(
  path: string,
  validate: (value: unknown) => value is T,
  options: {
    method?: string;
    body?: unknown;
    token?: string;
    signal?: AbortSignal;
  } = {},
): Promise<T> {
  const controller = new AbortController();
  const abort = () => controller.abort();
  options.signal?.addEventListener("abort", abort, { once: true });
  if (options.signal?.aborted) controller.abort();
  const timeout = window.setTimeout(abort, 15000);
  try {
    const response = await fetch(apiBase + path, {
      method: options.method ?? "GET",
      cache: "no-store",
      signal: controller.signal,
      headers: {
        ...(options.body === undefined
          ? {}
          : { "Content-Type": "application/json" }),
        ...(options.token ? { "X-Player-Token": options.token } : {}),
      },
      ...(options.body === undefined
        ? {}
        : { body: JSON.stringify(options.body) }),
    });
    const data: unknown = await response.json();
    if (!response.ok) {
      const code =
        object(data) && typeof data.code === "string"
          ? data.code
          : "REQUEST_FAILED";
      throw new ApiError(
        code,
        errorMessages[code] ??
          (object(data) && typeof data.message === "string"
            ? data.message
            : "Request failed. Please refresh and retry."),
      );
    }
    if (!validate(data))
      throw new ApiError(
        "INVALID_RESPONSE",
        "Unexpected backend response. Refresh or retry.",
        true,
      );
    return data;
  } catch (error) {
    if (error instanceof ApiError) throw error;
    throw new ApiError(
      "NETWORK",
      "Backend unavailable or request timed out. Please retry.",
      true,
    );
  } finally {
    window.clearTimeout(timeout);
    options.signal?.removeEventListener("abort", abort);
  }
}
const roomPath = (identity: Identity) =>
  `/api/rooms/${encodeURIComponent(identity.code)}`;
export const api = {
  health: (signal?: AbortSignal) =>
    request(
      "/api/health",
      (v): v is { status: "ok" } => object(v) && v.status === "ok",
      { signal },
    ),
  enter: (displayName: string, code?: string) =>
    request(
      code ? `/api/rooms/${encodeURIComponent(code)}/join` : "/api/rooms",
      isSession,
      { method: "POST", body: { displayName } },
    ),
  room: (id: Identity, signal?: AbortSignal) =>
    request(roomPath(id), isView, { token: id.token, signal }),
  command: (id: Identity, command: string, body: unknown) =>
    request(roomPath(id) + "/" + command, isView, {
      method: command === "settings" ? "PATCH" : "POST",
      body,
      token: id.token,
    }),
  move: (id: Identity, intent: Intent) =>
    request(
      roomPath(id) + "/actions/" + intent.type,
      (v): v is { view: View } => object(v) && isView(v.view),
      {
        method: "POST",
        token: id.token,
        body: {
          actionId: intent.actionId,
          ...(intent.type === "navigate"
            ? { destinationArticle: intent.destinationArticle }
            : {}),
        },
      },
    ),
  article: (id: Identity, signal?: AbortSignal) =>
    request(roomPath(id) + "/me/article", isArticle, {
      token: id.token,
      signal,
    }),
  search: (q: string, signal?: AbortSignal) =>
    request(
      "/api/wiki/search?q=" + encodeURIComponent(q),
      (v): v is { results: { title: string }[] } =>
        object(v) &&
        Array.isArray(v.results) &&
        v.results.every((r) => object(r) && typeof r.title === "string"),
      { signal },
    ),
};
