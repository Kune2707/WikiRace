import { act, renderHook } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { useGameBack } from "./useGameBack";
import { fixture } from "../lib/testFixtures";

it("native Back submits only a server BACK intent and reuses one guard", () => {
  const push = vi.spyOn(history, "pushState");
  const move = vi.fn();
  const view = fixture();
  view.me.canGoBack = true;
  const hook = renderHook(({ disabled }) => useGameBack(view, disabled, move), {
    initialProps: { disabled: false },
  });
  expect(push).toHaveBeenCalledOnce();
  act(() => window.dispatchEvent(new PopStateEvent("popstate")));
  expect(move).toHaveBeenCalledWith({
    actionId: expect.any(String),
    type: "back",
  });
  hook.rerender({ disabled: true });
  act(() => window.dispatchEvent(new PopStateEvent("popstate")));
  expect(move).toHaveBeenCalledOnce();
  hook.unmount();
  act(() => window.dispatchEvent(new PopStateEvent("popstate")));
  expect(move).toHaveBeenCalledOnce();
  expect(history.state?.wikiraceGuard).toBeUndefined();
});
it("does not intercept waiting/finished states and never invents previous article", () => {
  const move = vi.fn();
  const view = fixture();
  view.me.canGoBack = false;
  const hook = renderHook(({ current }) => useGameBack(current, false, move), {
    initialProps: { current: view },
  });
  act(() => window.dispatchEvent(new PopStateEvent("popstate")));
  expect(move).not.toHaveBeenCalled();
  hook.rerender({ current: fixture("FINISHED") });
  act(() => window.dispatchEvent(new PopStateEvent("popstate")));
  expect(move).not.toHaveBeenCalled();
});
