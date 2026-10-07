import type { View } from "./types";
export function fixture(status: View["room"]["status"] = "ACTIVE"): View {
  return {
    serverTime: "2026-10-07T00:00:00Z",
    room: {
      roomCode: "ABC234",
      version: 1,
      status,
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
          clickCount: 0,
          closeToTarget: true,
          currentArticle: null,
        },
        {
          playerId: "guest",
          displayName: "Sarah",
          host: false,
          ready: true,
          connected: true,
          clickCount: 7,
          closeToTarget: true,
          currentArticle: "SECRET",
        },
      ],
      startsAt: "2026-10-07T00:00:00Z",
      suddenDeathStartedAt: null,
      finishedAt: null,
      winnerPlayerId: null,
    },
    me: {
      playerId: "host",
      currentArticle: "Computer Science",
      clickCount: 0,
      canGoBack: false,
      movementRevision: 0,
    },
  };
}
