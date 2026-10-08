import type { ReactNode } from "react";

export default function GameEnvironment({ children }: { children: ReactNode }) {
  return <main className="game-viewport">{children}</main>;
}
