import { useEffect, useMemo, useRef } from "react";
import {
  ArrowLeft,
  RotateCw,
  MousePointer2,
  TriangleAlert,
  Trophy,
  LoaderCircle,
} from "lucide-react";
import PlayerList from "./PlayerList";
import { safeArticle } from "../lib/article";
import { movement, timer } from "../lib/game";
import type { Article, Intent, View } from "../lib/types";

export default function Race({
  view,
  now,
  article,
  articleError,
  retryArticle,
  disabled,
  move,
}: {
  view: View;
  now: number;
  article: Article | null;
  articleError: string;
  retryArticle: () => void;
  disabled: boolean;
  move: (intent: Intent) => void;
}) {
  const content = useRef<HTMLDivElement>(null);
  const html = useMemo(() => safeArticle(article?.html ?? ""), [article?.html]);
  const sudden = view.room.status === "SUDDEN_DEATH";
  const finished = view.room.status === "FINISHED";
  const winner = view.room.players.find(
    (p) => p.playerId === view.room.winnerPlayerId,
  );
  const close = view.room.players.filter(
    (p) => p.playerId !== view.me.playerId && p.closeToTarget,
  );
  useEffect(() => {
    if (content.current) content.current.scrollTop = 0;
  }, [article?.title, article?.movementRevision]);
  return (
    <div
      className={[
        "race",
        sudden ? "sudden-death" : "",
        !finished && close.length ? "close-pressure" : "",
        finished ? (winner?.playerId === view.me.playerId ? "race-won" : "race-lost") : "",
      ].filter(Boolean).join(" ")}
    >
      {sudden && (
        <div className="sudden-banner" role="status">
          <TriangleAlert size={18} /> SUDDEN DEATH
        </div>
      )}
      {!finished &&
        close.map((p) => (
          <div className="close-banner" role="status" key={p.playerId}>
            <TriangleAlert size={16} />
            {p.displayName} is one click away!
          </div>
        ))}
      {finished && (
        <section className="finish-banner" aria-label="Race result" role="status">
          <Trophy size={28} />
          <div>
            <span className="eyebrow">
              {winner?.playerId === view.me.playerId
                ? "YOU WON"
                : "RACE FINISHED"}
            </span>
            <h1>{winner?.displayName ?? "Winner"} wins</h1>
            <p>
              {view.room.settings.startArticle} to{" "}
              {view.room.settings.targetArticle} · {timer(view, now)} ·{" "}
              {winner?.clickCount ?? 0} clicks
            </p>
          </div>
        </section>
      )}
      <div className="race-grid">
        <article
          className="article-scroll"
          ref={content}
          aria-busy={!article || disabled}
        >
          <div className="article-heading">
            <div className="article-title">
              <span className="eyebrow">WIKIPEDIA</span>
              <h1>{view.me.currentArticle}</h1>
            </div>
            <div className="article-actions">
              <div className="click-counter">
                <MousePointer2 size={16} />
                <strong>{view.me.clickCount}</strong>
                <span>clicks</span>
              </div>
              <button
                className="icon-button back-button"
                title="Back"
                aria-label="Back"
                disabled={disabled || !article || !view.me.canGoBack || finished}
                onClick={() => move(movement("back"))}
              >
                <ArrowLeft size={20} />
              </button>
            </div>
          </div>
          {articleError ? (
            <div role="alert" className="article-failure">
              <TriangleAlert size={22} />
              <h2>Article unavailable</h2>
              <p>{articleError}</p>
              <button className="secondary" onClick={retryArticle}>
                <RotateCw size={16} />
                Retry Article
              </button>
            </div>
          ) : !article ? (
            <div className="article-loading" role="status">
              <LoaderCircle className="loading-spinner" size={20} />
              <span>Loading article...</span>
              <div className="article-skeleton" aria-hidden="true">
                <i /><i /><i /><i />
              </div>
            </div>
          ) : (
            <div
              className={
                "wiki-content " + (disabled || finished ? "blocked" : "")
              }
              key={`${article.title}:${article.movementRevision}`}
              onClick={(event) => {
                const anchor =
                  event.target instanceof Element
                    ? event.target.closest("a")
                    : null;
                if (!anchor || !event.currentTarget.contains(anchor)) return;
                event.preventDefault();
                const title = anchor.getAttribute("data-wiki-title");
                if (title && anchor.getAttribute("href") === "#") {
                  if (!disabled && !finished) move(movement("navigate", title));
                } else {
                  const href = anchor.getAttribute("href") ?? "";
                  if (href.startsWith("#") && href.length > 1) {
                    const target = content.current?.querySelector(
                      "#" + CSS.escape(href.slice(1)),
                    );
                    target?.scrollIntoView({
                      block: "start",
                      behavior: "auto",
                    });
                  }
                }
              }}
              dangerouslySetInnerHTML={{ __html: html }}
            />
          )}
          {article && (
            <footer className="article-credit">
              English Wikipedia · CC BY-SA · Revision{" "}
              {article.revisionId ?? "test"}
            </footer>
          )}
        </article>
        <PlayerList view={view} />
      </div>
    </div>
  );
}
