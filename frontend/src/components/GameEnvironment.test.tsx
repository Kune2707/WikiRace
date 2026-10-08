import { fireEvent, render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import GameEnvironment from "./GameEnvironment";

it("renders the application directly in a semantic viewport without scenery", () => {
  const { container } = render(<GameEnvironment><button>Screen</button></GameEnvironment>);
  expect(screen.getByRole("main")).toHaveClass("game-viewport");
  expect(screen.getByRole("button", { name: "Screen" })).toBeInTheDocument();
  expect(container.querySelector(".laptop, .keyboard, .desk-surface, .coffee-cup, .notebook")).toBeNull();
});

it("does not schedule decorative pointer animation", () => {
  const request = vi.fn();
  vi.stubGlobal("requestAnimationFrame", request);
  render(<GameEnvironment>Screen</GameEnvironment>);
  fireEvent.pointerMove(screen.getByRole("main"), { pointerType: "mouse", clientX: 100 });
  expect(request).not.toHaveBeenCalled();
});
