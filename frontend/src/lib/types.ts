export type RaceStatus =
  "WAITING" | "COUNTDOWN" | "ACTIVE" | "SUDDEN_DEATH" | "FINISHED";
export interface Settings {
  startArticle: string | null;
  targetArticle: string | null;
  unlimited: boolean;
  timeLimitSeconds: number | null;
}
export interface Player {
  playerId: string;
  displayName: string;
  host: boolean;
  ready: boolean;
  connected: boolean;
  clickCount: number;
  closeToTarget: boolean;
  currentArticle: string | null;
}
export interface View {
  serverTime: string;
  room: {
    roomCode: string;
    version: number;
    status: RaceStatus;
    hostPlayerId: string;
    settings: Settings;
    players: Player[];
    startsAt: string | null;
    suddenDeathStartedAt: string | null;
    finishedAt: string | null;
    winnerPlayerId: string | null;
  };
  me: {
    playerId: string;
    currentArticle: string | null;
    clickCount: number;
    canGoBack: boolean;
    movementRevision: number;
  };
}
export interface Session {
  playerId: string;
  playerSessionToken: string;
  view: View;
}
export interface Identity {
  code: string;
  token: string;
  playerId: string;
}
export interface Article {
  roomCode: string;
  playerId: string;
  movementRevision: number;
  roomVersion: number;
  title: string;
  html: string;
  sourceUrl: string;
  revisionId: number | null;
}
export interface Intent {
  actionId: string;
  type: "navigate" | "back";
  destinationArticle?: string;
}

export interface RoomEvent {
  type: string;
  roomCode: string;
  version: number;
  occurredAt: string;
  room: View["room"];
}
