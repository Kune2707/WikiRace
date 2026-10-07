import { fireEvent, render } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import GameEnvironment from "./GameEnvironment";

it("batches parallax without rerendering children and cancels pending work on unmount", () => {
  const child = vi.fn(() => <div>Screen</div>);
  const Child = child;
  let update: FrameRequestCallback = () => {};
  const request = vi.fn((callback: FrameRequestCallback) => {update = callback; return 7;});
  const cancel = vi.fn();
  vi.stubGlobal("requestAnimationFrame", request);
  vi.stubGlobal("cancelAnimationFrame", cancel);
  const {container, unmount} = render(<GameEnvironment><Child /></GameEnvironment>);
  const laptop = container.querySelector(".laptop") as HTMLDivElement;
  vi.spyOn(laptop, "getBoundingClientRect").mockReturnValue({left: 0, top: 0, width: 100, height: 100} as DOMRect);
  const main = container.querySelector("main")!;
  for (let i = 0; i < 5; i++) {
    const event = new Event("pointermove", {bubbles: true});
    Object.assign(event, {pointerType: "mouse", clientX: 100, clientY: 100});
    fireEvent(main, event);
  }
  expect(request).toHaveBeenCalledOnce();
  update(0);
  expect(laptop.style.getPropertyValue("--parallax-x")).toBe("3px");
  expect(child).toHaveBeenCalledOnce();
  const event = new Event("pointermove", {bubbles: true});
  Object.assign(event, {pointerType: "mouse", clientX: 0, clientY: 0});
  fireEvent(main, event);
  unmount();
  expect(cancel).toHaveBeenCalledWith(7);
});

it("disables pointer work under reduced motion and cleans up the preference listener", () => {
  const add = vi.fn(), remove = vi.fn(), request = vi.fn();
  vi.stubGlobal("matchMedia", vi.fn(() => ({matches: true, addEventListener: add, removeEventListener: remove})));
  vi.stubGlobal("requestAnimationFrame", request);
  const {container, unmount} = render(<GameEnvironment>Screen</GameEnvironment>);
  const event = new Event("pointermove", {bubbles: true});
  Object.assign(event, {pointerType: "mouse"});
  fireEvent(container.querySelector("main")!, event);
  expect(request).not.toHaveBeenCalled();
  unmount();
  expect(remove).toHaveBeenCalledWith("change", add.mock.calls[0][1]);
});
