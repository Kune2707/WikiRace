import { fireEvent, render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import Race from "./Race";
import RaceStatus from "./RaceStatus";
import { fixture } from "../lib/testFixtures";
import type { Article } from "../lib/types";
const article: Article = {
  roomCode: "ABC234",
  playerId: "host",
  roomVersion: 1,
  movementRevision: 0,
  title: "Computer Science",
  html: '<p><a href="#" data-wiki-title="Algorithm">Algorithm</a></p>',
  sourceUrl: "https://en.wikipedia.org/wiki/Computer_science",
  revisionId: 42,
};
const props = () => ({
  view: fixture(),
  now: Date.parse("2026-10-07T00:00:10Z"),
  article,
  articleError: "",
  retryArticle: vi.fn(),
  disabled: false,
  move: vi.fn(),
});
it("renders opponent warnings without revealing their article or alerting self", () => {
  render(<Race {...props()} />);
  expect(screen.getByText("Sarah is one click away!")).toBeInTheDocument();
  expect(screen.queryByText("Alex is one click away!")).not.toBeInTheDocument();
  expect(screen.queryByText("SECRET")).not.toBeInTheDocument();
});
it("intercepts internal links and submits only an intent", () => {
  const p = props();
  render(<Race {...p} />);
  fireEvent.click(screen.getByRole("link", { name: "Algorithm" }));
  expect(p.move).toHaveBeenCalledWith(
    expect.objectContaining({
      type: "navigate",
      destinationArticle: "Algorithm",
    }),
  );
});
it("blocks movement while a request is pending", () => {
  const p = props();
  render(<Race {...p} disabled />);
  fireEvent.click(screen.getByRole("link", { name: "Algorithm" }));
  expect(p.move).not.toHaveBeenCalled();
  expect(screen.getByRole("button", { name: "Back" })).toBeDisabled();
});
it("uses the authoritative Back availability and generates a Back intent", () => {
  const p = props();
  p.view.me.canGoBack = true;
  render(<Race {...p} />);
  fireEvent.click(screen.getByRole("button", { name: "Back" }));
  expect(p.move).toHaveBeenCalledWith(
    expect.objectContaining({ type: "back" }),
  );
});
it("shows server-confirmed sudden death and finish without fetching future results", () => {
  const p = props();
  p.view.room.status = "SUDDEN_DEATH";
  p.view.room.suddenDeathStartedAt = "2026-10-07T00:00:02Z";
  const { rerender } = render(<><RaceStatus view={p.view} now={p.now} /><Race {...p} /></>);
  expect(screen.getByText("+00:08")).toBeInTheDocument();
  p.view.room.status = "FINISHED";
  p.view.room.winnerPlayerId = "host";
  p.view.room.finishedAt = "2026-10-07T00:00:10Z";
  rerender(<Race {...p} />);
  expect(
    screen.getByRole("heading", { name: "Alex wins" }),
  ).toBeInTheDocument();
  fireEvent.click(screen.getByRole("link", { name: "Algorithm" }));
  expect(p.move).not.toHaveBeenCalled();
});
it("distinguishes the winner and loss presentation while preserving blocked movement", () => {
  const p = props();
  p.view.room.status = "FINISHED";
  p.view.room.winnerPlayerId = "guest";
  p.view.room.finishedAt = "2026-10-07T00:00:10Z";
  const {container} = render(<Race {...p} />);
  expect(container.querySelector(".race-lost")).toBeInTheDocument();
  expect(screen.getByRole("heading", {name: "Sarah wins"})).toBeInTheDocument();
  expect(screen.queryByText("YOU WON")).not.toBeInTheDocument();
  expect(container.querySelector(".close-pressure")).not.toBeInTheDocument();
  expect(screen.getByRole("button", {name: "Back"})).toBeDisabled();
});
it("shows an accessible article loading state and a retryable failure", () => {
  const p = props();
  const {rerender} = render(<Race {...p} article={null} />);
  expect(screen.getByText("Loading article...")).toBeInTheDocument();
  rerender(<Race {...p} article={null} articleError="Wikipedia is unavailable" />);
  expect(screen.getByRole("alert")).toHaveTextContent("Article unavailable");
  fireEvent.click(screen.getByRole("button", {name: "Retry Article"}));
  expect(p.retryArticle).toHaveBeenCalledOnce();
  expect(p.move).not.toHaveBeenCalled();
});
it("renders hostile display names as text and external metadata never becomes a move", () => {
  const p = props();
  const name = '<img src=x onerror="alert(1)">';
  p.view.room.players[1].displayName = name;
  p.article = {...article, html: '<p><a href="https://evil.test/wiki/Algorithm" data-wiki-title="Algorithm">External target</a></p><p><a href="javascript:alert(1)" data-wiki-title="Algorithm">Script target</a></p>'};
  const {container} = render(<Race {...p} />);
  expect(container.querySelector("img")).toBeNull();
  expect(container.querySelector("[onerror]")).toBeNull();
  expect(screen.getByText(name)).toBeInTheDocument();
  fireEvent.click(screen.getByText("External target"));
  fireEvent.click(screen.getByText("Script target"));
  expect(p.move).not.toHaveBeenCalled();
});
