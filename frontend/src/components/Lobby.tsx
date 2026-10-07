import { useEffect, useState } from "react";
import { Copy, Check, Play, Clock } from "lucide-react";
import ArticlePicker from "./ArticlePicker";
import PlayerList from "./PlayerList";
import type { Settings, View } from "../lib/types";

export default function Lobby({
  view,
  busy,
  command,
}: {
  view: View;
  busy: boolean;
  command: (name: string, body: unknown) => Promise<boolean>;
}) {
  const settings = view.room.settings;
  const host = view.me.playerId === view.room.hostPlayerId;
  const me = view.room.players.find((p) => p.playerId === view.me.playerId)!;
  const hostConnected = view.room.players.some((p) => p.host && p.connected);
  const [start, setStart] = useState(settings.startArticle ?? "");
  const [target, setTarget] = useState(settings.targetArticle ?? "");
  const [selectedStart, setSelectedStart] = useState(settings.startArticle);
  const [selectedTarget, setSelectedTarget] = useState(settings.targetArticle);
  const [unlimited, setUnlimited] = useState(settings.unlimited);
  const [minutes, setMinutes] = useState(
    (settings.timeLimitSeconds ?? 300) / 60,
  );
  const [message, setMessage] = useState("");
  const [copied, setCopied] = useState(false);
  useEffect(() => {
    setStart(settings.startArticle ?? "");
    setSelectedStart(settings.startArticle);
    setTarget(settings.targetArticle ?? "");
    setSelectedTarget(settings.targetArticle);
    setUnlimited(settings.unlimited);
    setMinutes((settings.timeLimitSeconds ?? 300) / 60);
  }, [
    settings.startArticle,
    settings.targetArticle,
    settings.unlimited,
    settings.timeLimitSeconds,
  ]);
  const draft: Settings = {
    startArticle: selectedStart,
    targetArticle: selectedTarget,
    unlimited,
    timeLimitSeconds: unlimited ? null : minutes * 60,
  };
  const dirty =
    JSON.stringify(draft) !== JSON.stringify(settings) ||
    start !== (selectedStart ?? "") ||
    target !== (selectedTarget ?? "");
  const valid =
    start === selectedStart &&
    target === selectedTarget &&
    start !== target &&
    (unlimited || (Number.isInteger(minutes) && minutes >= 3));
  const canStart =
    !busy &&
    !dirty &&
    !!settings.startArticle &&
    !!settings.targetArticle &&
    view.room.players.length >= 2 &&
    view.room.players.every((p) => p.ready);
  return (
    <div className="lobby screen-body">
      <div className="lobby-heading">
        <div>
          <span className="eyebrow">LOBBY</span>
          <h1>Room {view.room.roomCode}</h1>
        </div>
        <button
          className="secondary"
          type="button"
          onClick={async () => {
            try {
              await navigator.clipboard.writeText(
                `${location.origin}/room/${view.room.roomCode}`,
              );
              setCopied(true);
              setMessage("Invite link copied.");
            } catch {
              setMessage(
                `Invite link: ${location.origin}/room/${view.room.roomCode}`,
              );
            }
          }}
        >
          {copied ? <Check size={16} /> : <Copy size={16} />} Copy Invite Link
        </button>
      </div>
      <div className="lobby-grid">
        <section className="race-settings">
          <h2>Race settings</h2>
          {host ? (
            <form
              onSubmit={async (e) => {
                e.preventDefault();
                if (valid && (await command("settings", draft)))
                  setMessage("Race settings saved.");
              }}
            >
              <ArticlePicker
                label="Start article"
                value={start}
                selected={selectedStart}
                disabled={busy}
                onChange={(value, selected) => {
                  setStart(value);
                  setSelectedStart(selected);
                }}
              />
              <ArticlePicker
                label="Target article"
                value={target}
                selected={selectedTarget}
                disabled={busy}
                onChange={(value, selected) => {
                  setTarget(value);
                  setSelectedTarget(selected);
                }}
              />
              <div className="timer-settings">
                <label htmlFor="minutes">
                  <Clock size={16} /> Time limit
                </label>
                <div className="timer-row">
                  <input
                    id="minutes"
                    type="number"
                    min={3}
                    max={35791394}
                    step={1}
                    value={minutes}
                    disabled={busy || unlimited}
                    onChange={(e) => setMinutes(Number(e.target.value))}
                  />
                  <span>minutes</span>
                  <label className="checkbox">
                    <input
                      type="checkbox"
                      checked={unlimited}
                      disabled={busy}
                      onChange={(e) => setUnlimited(e.target.checked)}
                    />{" "}
                    Unlimited
                  </label>
                </div>
              </div>
              <button type="submit" disabled={busy || !dirty || !valid}>
                Save Settings
              </button>
            </form>
          ) : (
            <dl className="settings-summary">
              <dt>Start</dt>
              <dd>{settings.startArticle ?? "Not selected"}</dd>
              <dt>Target</dt>
              <dd>{settings.targetArticle ?? "Not selected"}</dd>
              <dt>Time limit</dt>
              <dd>
                {settings.unlimited
                  ? "Unlimited"
                  : `${(settings.timeLimitSeconds ?? 0) / 60} minutes`}
              </dd>
            </dl>
          )}
          <p className="lobby-note">
            {!hostConnected
              ? "Host disconnected. Waiting for them to reconnect."
              : view.room.players.length < 2
              ? "Waiting for another player"
              : !view.room.players.every((p) => p.ready)
                ? "Waiting for everyone to be ready"
                : "Everyone is ready"}
          </p>
          {message && (
            <p className="status-note" role="status">
              {message}
            </p>
          )}
        </section>
        <PlayerList view={view} lobby />
      </div>
      <footer className="lobby-footer">
        <button
          className={me.ready ? "secondary" : ""}
          disabled={busy || dirty}
          onClick={() => void command("ready", { ready: !me.ready })}
        >
          <Check size={16} />
          {me.ready ? "Not Ready" : "Ready"}
        </button>
        {host && (
          <button
            disabled={!canStart}
            onClick={() => void command("start", {})}
          >
            <Play size={16} />
            Start Race
          </button>
        )}
      </footer>
    </div>
  );
}
