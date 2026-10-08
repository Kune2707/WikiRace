import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router";
import {
  BookOpen,
  ArrowRight,
  Volume2,
  VolumeX,
  RotateCw,
  LogOut,
  Wifi,
  WifiOff,
  LoaderCircle,
} from "lucide-react";
import GameEnvironment from "./components/GameEnvironment";
import Lobby from "./components/Lobby";
import Race from "./components/Race";
import RaceStatus from "./components/RaceStatus";
import { useRoom } from "./hooks/useRoom";
import { useGameBack } from "./hooks/useGameBack";
import { api } from "./lib/api";
import { normalizeCode, validCode, validName } from "./lib/game";
import { playSound, unlockSound } from "./lib/sound";
import type { Identity } from "./lib/types";

function readIdentity(storage: Storage, key: string): Identity | null {
  try {
    const value: unknown = JSON.parse(storage.getItem(key) ?? "null");
    if (
      typeof value === "object" &&
      value !== null &&
      "code" in value &&
      "token" in value &&
      typeof value.code === "string" &&
      validCode(value.code) &&
      typeof value.token === "string" &&
      value.token.length > 0
    )
      return {
        code: normalizeCode(value.code),
        token: value.token,
        playerId: "",
      };
  } catch {
    /* Ignore corrupt or blocked storage. */
  }
  return null;
}

export default function App() {
  const game = useRoom();
  const navigate = useNavigate();
  const { code: routeCode } = useParams();
  const [name, setName] = useState("");
  const [code, setCode] = useState(routeCode ?? "");
  const [muted, setMuted] = useState(false);
  const [health, setHealth] = useState("Checking backend...");
  const [healthAttempt, setHealthAttempt] = useState(0);
  const [saved, setSaved] = useState<Identity | null>(null);
  const previousStatus = useRef("");
  const previousCount = useRef(0);
  const previousClose = useRef(new Set<string>());
  const [goUntil, setGoUntil] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setCode(routeCode ?? "");
    try {
      const tab = readIdentity(sessionStorage, "wikirace.session");
      if (routeCode && tab?.code === normalizeCode(routeCode))
        void game.resume(tab, controller.signal);
      else if (game.identity) game.reset();
      setSaved(
        routeCode
          ? readIdentity(
              localStorage,
              "wikirace.room." + normalizeCode(routeCode),
            )
          : null,
      );
    } catch {
      /* Storage is optional. */
    }
    // Route transitions select identity once, not on every state update.
    return () => controller.abort();
  }, [routeCode]);
  useEffect(() => {
    const controller = new AbortController();
    void api
      .health(controller.signal)
      .then(() => {
        if (!controller.signal.aborted) setHealth("Backend connected");
      })
      .catch(() => {
        if (!controller.signal.aborted) setHealth("Backend unavailable");
      });
    return () => controller.abort();
  }, [healthAttempt]);

  const view = game.view;
  useGameBack(
    view,
    game.busy || !!game.pending || !game.connected,
    (intent) => void game.perform("", {}, intent),
  );
  const countdown =
    view?.room.status === "COUNTDOWN" && view.room.startsAt
      ? Math.max(
          1,
          Math.ceil((Date.parse(view.room.startsAt) - game.now) / 1000),
        )
      : 0;
  useEffect(() => {
    if (!view) {
      previousStatus.current = "";
      previousClose.current.clear();
      return;
    }
    const status = view.room.status;
    if (countdown && countdown !== previousCount.current && !muted)
      playSound("countdown");
    previousCount.current = countdown;
    if (previousStatus.current === "COUNTDOWN" && status === "ACTIVE") {
      setGoUntil(performance.now() + 650);
      if (!muted) playSound("go");
    }
    if (
      status === "FINISHED" &&
      previousStatus.current !== "FINISHED" &&
      !muted
    )
      playSound(view.room.winnerPlayerId === view.me.playerId ? "win" : "lose");
    const close = new Set(
      view.room.players
        .filter(
          (p) =>
            p.playerId !== view.me.playerId &&
            p.closeToTarget &&
            ["ACTIVE", "SUDDEN_DEATH"].includes(status),
        )
        .map((p) => p.playerId),
    );
    if ([...close].some((id) => !previousClose.current.has(id)) && !muted)
      playSound("close");
    previousClose.current = close;
    previousStatus.current = status;
  }, [view, countdown, muted]);

  const enter = async (joining: boolean) => {
    unlockSound();
    if (!validName(name)) {
      game.setError(
        "Use a display name of 2–20 letters, numbers, spaces or . _ ' -",
      );
      return;
    }
    if (joining && !validCode(code)) {
      game.setError("Enter a valid six-character room code.");
      return;
    }
    const roomCode = await game.enter(
      name,
      joining ? normalizeCode(code) : undefined,
    );
    if (roomCode) navigate("/room/" + roomCode);
  };
  return (
    <GameEnvironment>
      <div
        className="application"
        onPointerDown={unlockSound}
        onKeyDown={unlockSound}
      >
        <header className="app-header">
          <div className="wordmark">
            <BookOpen size={21} />
            <span>WikiRace</span>
          </div>
          {view && (
            <div className="header-room">
              <span className="eyebrow">ROOM</span>
              <strong>{view.room.roomCode}</strong>
            </div>
          )}
          {view && ["ACTIVE", "SUDDEN_DEATH", "FINISHED"].includes(view.room.status) && (
            <RaceStatus view={view} now={game.now} />
          )}
          <div className="header-actions">
            <span
              className="connection-state"
              role="status"
              title={game.identity ? (game.connected ? "Connected" : "Backend unavailable") : health}
            >
              {game.identity ? (
                game.connected ? (
                  <Wifi size={14} />
                ) : (
                  <WifiOff size={14} />
                )
              ) : health === "Backend connected" ? (
                <Wifi size={14} />
              ) : (
                <WifiOff size={14} />
              )}
              {game.identity
                ? game.connected
                  ? "Connected"
                  : "Backend unavailable"
                : health}
            </span>
            <button
              className="icon-button"
              title={muted ? "Unmute sounds" : "Mute sounds"}
              aria-label={muted ? "Unmute sounds" : "Mute sounds"}
              aria-pressed={muted}
              onClick={() => {
                unlockSound();
                setMuted((v) => !v);
              }}
            >
              {muted ? <VolumeX size={18} /> : <Volume2 size={18} />}
            </button>
            {game.identity && (
              <button
                className="icon-button"
                title="Return to home"
                aria-label="Return to home"
                disabled={game.busy || !!game.pending}
                onClick={() => {
                  game.reset();
                  navigate("/");
                }}
              >
                <LogOut size={17} />
              </button>
            )}
          </div>
        </header>
        {game.error && (
          <div className="error-banner" role="alert">
            <span>{game.error}</span>
            {game.pending ? (
              <button
                disabled={game.busy}
                className="secondary"
                onClick={() => void game.perform("", {}, game.pending!)}
              >
                <RotateCw size={15} />
                Retry Movement
              </button>
            ) : (
              game.identity && (
                <button
                  className="icon-button"
                  title="Refresh room"
                  aria-label="Refresh room"
                  onClick={() => {
                    game.setError("");
                    void game.refresh();
                  }}
                >
                  <RotateCw size={16} />
                </button>
              )
            )}
          </div>
        )}
        {game.identity && !game.connected && (
          <div className="reconnect-banner" role="status">
            <LoaderCircle className="loading-spinner" size={16} />
            Room disconnected. Gameplay paused until server confirmation.
          </div>
        )}
        {!game.identity ? (
          <div className="landing screen-body">
            <div className="landing-title">
              <span className="eyebrow">THE WIKIPEDIA RACE</span>
              <h1>WikiRace</h1>
              <div className="title-divider" />
              <p>One starting article. One target. Your route.</p>
            </div>
            <form
              className="entry-form"
              onSubmit={(e) => {
                e.preventDefault();
                void enter(false);
              }}
            >
              <label htmlFor="display-name">Display name</label>
              <input
                id="display-name"
                value={name}
                maxLength={20}
                autoComplete="nickname"
                placeholder="Your name"
                onChange={(e) => setName(e.target.value)}
                disabled={game.busy}
              />
              <button disabled={game.busy} type="submit">
                Create Room <ArrowRight size={17} />
              </button>
              <div className="entry-divider">
                <span>or join a room</span>
              </div>
              <label htmlFor="room-code">Room code</label>
              <div className="join-row">
                <input
                  id="room-code"
                  value={code}
                  maxLength={6}
                  autoComplete="off"
                  autoCapitalize="characters"
                  placeholder="ABC234"
                  onChange={(e) => setCode(normalizeCode(e.target.value))}
                  disabled={game.busy}
                />
                <button
                  type="button"
                  className="secondary"
                  disabled={game.busy}
                  onClick={() => void enter(true)}
                >
                  Join Room
                </button>
              </div>
              {saved && (
                <button
                  className="text-button"
                  type="button"
                  disabled={game.busy}
                  onClick={() => {
                    unlockSound();
                    void game.resume(saved);
                  }}
                >
                  Resume Session
                </button>
              )}
              {health === "Backend unavailable" && (
                <button
                  className="text-button"
                  type="button"
                  onClick={() => setHealthAttempt((n) => n + 1)}
                >
                  <RotateCw size={15} />
                  Retry Connection
                </button>
              )}
            </form>
            <footer className="landing-footer">
              <span>ENGLISH WIKIPEDIA</span>
              <span>2–4 PLAYERS</span>
              <span>NO ACCOUNTS</span>
            </footer>
          </div>
        ) : !view ? (
          <div className="screen-body loading-room" role="status">
            <LoaderCircle className="loading-spinner" size={24} />
            Loading room...
          </div>
        ) : view.room.status === "WAITING" ||
          view.room.status === "COUNTDOWN" ? (
          <div className="lobby-wrapper">
            <Lobby
              view={view}
              busy={game.busy || view.room.status === "COUNTDOWN"}
              command={(command, body) => game.perform(command, body)}
            />
            {view.room.status === "COUNTDOWN" && (
              <div className="countdown" role="status" aria-live="polite">
                <span>GET READY</span>
                <strong>{countdown}</strong>
                <p>
                  {view.room.settings.startArticle} →{" "}
                  {view.room.settings.targetArticle}
                </p>
              </div>
            )}
          </div>
        ) : (
          <Race
            view={view}
            now={game.now}
            article={game.article}
            articleError={game.articleError}
            retryArticle={game.retryArticle}
            disabled={game.busy || !!game.pending || !game.connected}
            move={(intent) => void game.perform("", {}, intent)}
          />
        )}
        {view?.room.status === "ACTIVE" && performance.now() < goUntil && (
          <div className="countdown go" role="status">
            <strong>GO</strong>
          </div>
        )}
        {game.busy && (
          <div className="request-progress" role="status">
            <LoaderCircle className="loading-spinner" size={14} />
            {game.pending ? "Confirming movement..." : game.identity ? "Saving..." : "Waiting for server..."}
          </div>
        )}
      </div>
    </GameEnvironment>
  );
}
