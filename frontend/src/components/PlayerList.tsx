import {
  Crown,
  CircleCheck,
  Circle,
  Wifi,
  WifiOff,
  TriangleAlert,
} from "lucide-react";
import { opponentLocation } from "../lib/game";
import type { View } from "../lib/types";

export default function PlayerList({
  view,
  lobby = false,
}: {
  view: View;
  lobby?: boolean;
}) {
  return (
    <section className="players" aria-label="Players">
      <h2>
        Players <span>{view.room.players.length}/4</span>
      </h2>
      <ul>
        {view.room.players.map((player) => {
          const self = player.playerId === view.me.playerId;
          const close = !self && player.closeToTarget && !lobby;
          return (
            <li
              className={close ? "player close" : "player"}
              key={player.playerId}
            >
              <div className="player-top">
                <span className="avatar" aria-hidden="true">
                  {player.displayName.slice(0, 1).toUpperCase()}
                </span>
                <strong>
                  {player.displayName}
                  {self && <small> You</small>}
                </strong>
                <span className="player-badges">
                  <span>{player.host && <Crown size={15} aria-label="Host" />}</span>
                  {player.connected ? (
                    <Wifi size={14} aria-label="Connected" />
                  ) : (
                    <WifiOff size={14} aria-label="Disconnected" />
                  )}
                </span>
              </div>
              {lobby ? (
                <div className={player.ready ? "readiness ready" : "readiness"}>
                  {player.ready ? (
                    <CircleCheck size={14} />
                  ) : (
                    <Circle size={14} />
                  )}{" "}
                  {player.ready ? "Ready" : "Not ready"}
                </div>
              ) : (
                <>
                  <p className="player-location">
                    {close && <TriangleAlert size={14} />}{" "}
                    {self ? view.me.currentArticle : opponentLocation(player)}
                  </p>
                  <small>{player.clickCount} clicks</small>
                </>
              )}
            </li>
          );
        })}
      </ul>
    </section>
  );
}
