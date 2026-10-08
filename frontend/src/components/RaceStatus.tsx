import { timer } from "../lib/game";
import type { View } from "../lib/types";

export default function RaceStatus({ view, now }: { view: View; now: number }) {
  const sudden = view.room.status === "SUDDEN_DEATH";
  return (
    <div className={sudden ? "header-race-status sudden-status" : "header-race-status"}>
      <div className="target">
        <span className="eyebrow">TARGET</span>
        <strong>{view.room.settings.targetArticle}</strong>
      </div>
      <div className="race-time">
        <span className="eyebrow">
          {sudden ? "SUDDEN DEATH" : view.room.status === "FINISHED" ? "FINAL TIME" : view.room.settings.unlimited ? "ELAPSED" : "REMAINING"}
        </span>
        <strong>{timer(view, now)}</strong>
      </div>
    </div>
  );
}
