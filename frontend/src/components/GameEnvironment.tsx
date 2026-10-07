import { useEffect, useRef } from "react";
import type { ReactNode } from "react";

export default function GameEnvironment({ children }: { children: ReactNode }) {
  const frame = useRef<HTMLDivElement>(null);
  const pendingFrame = useRef<number | null>(null);
  const offset = useRef({ x: 0, y: 0 });
  const reset = () => {
    if (pendingFrame.current !== null) cancelAnimationFrame(pendingFrame.current);
    pendingFrame.current = null;
    frame.current?.style.setProperty("--parallax-x", "0px");
    frame.current?.style.setProperty("--parallax-y", "0px");
  };
  useEffect(() => {
    const motion = window.matchMedia("(prefers-reduced-motion: reduce)");
    motion.addEventListener("change", reset);
    return () => {
      motion.removeEventListener("change", reset);
      reset();
    };
  }, []);
  return (
    <main
      className="environment"
      onPointerMove={(event) => {
        if (
          event.pointerType !== "mouse" ||
          window.innerWidth <= 650 ||
          window.matchMedia("(prefers-reduced-motion: reduce)").matches
        )
          return;
        const box = frame.current?.getBoundingClientRect();
        if (box) {
          offset.current = {
            x: Math.max(
              -3,
              Math.min(
                3,
                ((event.clientX - box.left - box.width / 2) / box.width) * 6,
              ),
            ),
            y: Math.max(
              -3,
              Math.min(
                3,
                ((event.clientY - box.top - box.height / 2) / box.height) * 6,
              ),
            ),
          };
          if (pendingFrame.current === null)
            pendingFrame.current = requestAnimationFrame(() => {
              pendingFrame.current = null;
              frame.current?.style.setProperty("--parallax-x", `${offset.current.x}px`);
              frame.current?.style.setProperty("--parallax-y", `${offset.current.y}px`);
            });
        }
      }}
      onPointerLeave={reset}
    >
      <div className="room-background" aria-hidden="true">
        <div className="window-frame">
          <i />
          <i />
          <i />
          <i />
        </div>
        <div className="wall-shelf">
          <i />
          <i />
          <i />
        </div>
      </div>
      <div className="desk-surface" aria-hidden="true" />
      <div className="desk-lamp" aria-hidden="true">
        <div className="lamp-light" />
        <div className="lamp-shade" />
        <div className="lamp-arm" />
        <div className="lamp-foot" />
      </div>
      <div className="coffee-cup" aria-hidden="true">
        <div className="coffee" />
      </div>
      <div className="notebook" aria-hidden="true">
        <i />
        <span />
      </div>
      <div
        className="laptop"
        ref={frame}
      >
        <div className="laptop-lid">
          <span className="camera" aria-hidden="true" />
          <div className="laptop-display">{children}</div>
          <div className="lid-bottom" aria-hidden="true" />
        </div>
        <div className="laptop-base" aria-hidden="true">
          <div className="keyboard">
            {Array.from({ length: 60 }, (_, i) => (
              <i key={i} data-key={"1234567890QWERTYUIOPASDFGHJKLZXCVBNM"[i] ?? ""} />
            ))}
          </div>
          <div className="trackpad" />
        </div>
      </div>
    </main>
  );
}
