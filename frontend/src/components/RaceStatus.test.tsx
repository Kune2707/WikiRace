import { render, screen } from "@testing-library/react";
import { expect, it } from "vitest";
import RaceStatus from "./RaceStatus";
import { fixture } from "../lib/testFixtures";

it("shows the server-selected target and timing in the navigation status", () => {
  const view = fixture();
  render(<RaceStatus view={view} now={Date.parse(view.room.startsAt!)} />);
  expect(screen.getByText(view.room.settings.targetArticle!)).toBeInTheDocument();
  expect(screen.getByText("REMAINING")).toBeInTheDocument();
});

it("preserves sudden death and final timing labels", () => {
  const view = fixture();
  view.room.status = "SUDDEN_DEATH";
  view.room.suddenDeathStartedAt = "2026-10-07T00:00:00Z";
  const now = Date.parse("2026-10-07T00:00:08Z");
  const { rerender } = render(<RaceStatus view={view} now={now} />);
  expect(screen.getByText("SUDDEN DEATH")).toBeInTheDocument();
  expect(screen.getByText("+00:08")).toBeInTheDocument();
  view.room.status = "FINISHED";
  view.room.finishedAt = "2026-10-07T00:00:08Z";
  rerender(<RaceStatus view={view} now={now} />);
  expect(screen.getByText("FINAL TIME")).toBeInTheDocument();
});
