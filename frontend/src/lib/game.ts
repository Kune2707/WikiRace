import type { Intent, Player, View } from "./types";

export function validName(name: string) {
  const length = [...name].length;
  return length >= 2 && length <= 20 && /^[\p{L}\p{N} ._'\-]+$/u.test(name);
}
export function normalizeCode(code: string) {
  return code.trim().toUpperCase();
}
export function validCode(code: string) {
  return /^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}$/.test(normalizeCode(code));
}
export function formatTime(seconds: number) {
  const total = Math.max(0, Math.floor(seconds));
  return `${String(Math.floor(total / 60)).padStart(2, "0")}:${String(total % 60).padStart(2, "0")}`;
}
export function timer(view: View, now: number) {
  const room = view.room;
  const end = room.finishedAt ? Date.parse(room.finishedAt) : now;
  const start = room.startsAt ? Date.parse(room.startsAt) : end;
  if (room.status === "SUDDEN_DEATH" && room.suddenDeathStartedAt)
    return (
      "+" + formatTime((end - Date.parse(room.suddenDeathStartedAt)) / 1000)
    );
  if (room.status === "FINISHED" || room.settings.unlimited)
    return formatTime((end - start) / 1000);
  return formatTime(
    (room.settings.timeLimitSeconds ?? 0) - (end - start) / 1000,
  );
}
export function movement(
  type: Intent["type"],
  destinationArticle?: string,
): Intent {
  if (type === "navigate" && !destinationArticle)
    throw new Error("Missing navigation destination");
  return {
    actionId: crypto.randomUUID(),
    type,
    ...(type === "navigate" ? { destinationArticle } : {}),
  };
}
export function opponentLocation(player: Player) {
  return player.closeToTarget ? "CLOSE!" : (player.currentArticle ?? "Waiting");
}
export function newerView(current: View | null, incoming: View) {
  return (
    !current ||
    (incoming.room.roomCode === current.room.roomCode &&
      incoming.me.playerId === current.me.playerId &&
      incoming.room.version >= current.room.version &&
      incoming.me.movementRevision >= current.me.movementRevision)
  );
}
