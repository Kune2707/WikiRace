import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { beforeEach, expect, it, vi } from "vitest";
import App from "./App";
import { api } from "./lib/api";
import type { Identity } from "./lib/types";
vi.mock("./realtime/roomStream", () => ({
  connectRoom: vi.fn((_id: Identity, _event: unknown, ready: () => void) => {
    queueMicrotask(ready);
    return vi.fn();
  }),
}));
import { fixture } from "./lib/testFixtures";

beforeEach(() => {
  vi.spyOn(api, "health").mockResolvedValue({ status: "ok" });
});
function app(path = "/") {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/" element={<App />} />
        <Route path="/room/:code" element={<App />} />
      </Routes>
    </MemoryRouter>,
  );
}
it("loads the full-screen interface, validates inputs and creates a server room", async () => {
  const view = fixture("WAITING");
  view.room.settings.startArticle = null;
  view.room.settings.targetArticle = null;
  const enter = vi.spyOn(api, "enter").mockResolvedValue({
    playerId: "host",
    playerSessionToken: "private-token",
    view,
  });
  vi.spyOn(api, "room").mockResolvedValue(view);
  app();
  fireEvent.click(screen.getByRole("button", { name: "Create Room" }));
  expect(enter).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText("Display name"), {
    target: { value: "Alex" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Create Room" }));
  await screen.findByRole("heading", { name: "Room ABC234" });
  expect(enter).toHaveBeenCalledWith("Alex", undefined);
  expect(document.querySelector(".game-viewport")).toBeInTheDocument();
  expect(document.querySelector(".laptop-display")).toBeNull();
  expect(document.querySelector(".header-room")).toHaveTextContent("ABC234");
  expect(screen.getByRole("button", { name: "Start Race" })).toBeDisabled();
});
it("joins an invite without inheriting another tab identity or exposing tokens in links", async () => {
  const view = fixture("WAITING");
  view.me.playerId = "guest";
  localStorage.setItem(
    "wikirace.room.ABC234",
    JSON.stringify({ code: "ABC234", token: "host-token", playerId: "host" }),
  );
  const enter = vi.spyOn(api, "enter").mockResolvedValue({
    playerId: "guest",
    playerSessionToken: "guest-token",
    view,
  });
  vi.spyOn(api, "room").mockResolvedValue(view);
  app("/room/ABC234");
  expect(screen.getByLabelText("Room code")).toHaveValue("ABC234");
  fireEvent.change(screen.getByLabelText("Display name"), {
    target: { value: "Sarah" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Join Room" }));
  await screen.findByRole("heading", { name: "Room ABC234" });
  expect(enter).toHaveBeenCalledWith("Sarah", "ABC234");
  expect(screen.queryByText("host-token")).not.toBeInTheDocument();
});
it("renders countdown using server startsAt and keeps gameplay withheld", async () => {
  const view = fixture("COUNTDOWN");
  view.serverTime = new Date().toISOString();
  view.room.startsAt = new Date(Date.now() + 3000).toISOString();
  sessionStorage.setItem(
    "wikirace.session",
    JSON.stringify({
      code: "ABC234",
      token: "private-token",
      playerId: "host",
    }),
  );
  vi.spyOn(api, "room").mockResolvedValue(view);
  const article = vi.spyOn(api, "article");
  app("/room/ABC234");
  await screen.findByText("GET READY");
  await waitFor(() => expect(screen.getByText("3")).toBeInTheDocument());
  expect(article).not.toHaveBeenCalled();
  expect(screen.getByRole("button", { name: "Start Race" })).toBeDisabled();
});
