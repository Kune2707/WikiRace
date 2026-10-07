import { useEffect, useRef } from "react";
import { movement } from "../lib/game";
import type { Intent, View } from "../lib/types";

export function useGameBack(
  view: View | null,
  disabled: boolean,
  move: (intent: Intent) => void,
) {
  const latest = useRef({ view, disabled, move });
  latest.current = { view, disabled, move };
  const active =
    view?.room.status === "ACTIVE" || view?.room.status === "SUDDEN_DEATH";
  const code = view?.room.roomCode;
  useEffect(() => {
    if (!active || !code) return;
    const url = location.href;
    const guard = () =>
      history.pushState({ ...history.state, wikiraceGuard: code }, "", url);
    if (history.state?.wikiraceGuard !== code) guard();
    const back = () => {
      if (location.href !== url) return;
      // This same-URL entry catches native Back; only the server chooses the destination.
      guard();
      const current = latest.current;
      if (!current.disabled && current.view?.me.canGoBack)
        current.move(movement("back"));
    };
    window.addEventListener("popstate", back);
    return () => {
      window.removeEventListener("popstate", back);
      if (history.state?.wikiraceGuard === code) {
        const state = { ...history.state };
        delete state.wikiraceGuard;
        history.replaceState(state, "", location.href);
      }
    };
  }, [active, code]);
}
