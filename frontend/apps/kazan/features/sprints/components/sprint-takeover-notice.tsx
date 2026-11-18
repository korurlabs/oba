/**
 * SprintTakeoverNotice Component
 *
 * Warns that boards of a new or planned sprint already run an active sprint,
 * which the sprint can only take over by closing it. The blocking boards and
 * sprints are listed in an InfoTip, so the dialog keeps one line of text. A
 * board whose tickets go to its own backlog, and a board the user cannot
 * manage, are marked there. Each repeating series that stops because its last
 * running sprint is closed gets its own line, since the repeat ends without
 * any other trace in the dialog.
 */

"use client";

import { useTranslations } from "next-intl";
import { AlertCircle, Lock } from "lucide-react";
import { InlineAlert } from "@/components/ui/inline-alert";
import { InfoTip } from "@/components/ui/info-tip";
import type { SprintStartConflictResponse } from "../schemas";

/**
 * One blocking board and its active sprint. The start dialog passes the
 * preview rows; the create dialog builds them from the sprint list and the
 * child boards.
 */
export type SprintTakeoverConflict = Pick<
  SprintStartConflictResponse,
  | "boardId"
  | "boardName"
  | "sprintId"
  | "sprintName"
  | "movesToBacklog"
  | "canManage"
  | "endsSeriesName"
>;

interface SprintTakeoverNoticeProps {
  conflicts: SprintTakeoverConflict[];
  /** Name of the sprint that takes over. */
  sprintName: string;
}

/**
 * Number of the sprint's own boards that hold a blocking sprint. Backlog rows
 * are boards outside the sprint, so they are not "selected boards". Rows of
 * linked boards can be missing when the user is not a member of them; the
 * count then falls back to every listed board.
 */
function countSelectedBoards(conflicts: SprintTakeoverConflict[]): number {
  const linked = new Set(
    conflicts.filter((conflict) => !conflict.movesToBacklog).map((conflict) => conflict.boardId)
  );
  if (linked.size > 0) return linked.size;
  return new Set(conflicts.map((conflict) => conflict.boardId)).size;
}

export function SprintTakeoverNotice({ conflicts, sprintName }: SprintTakeoverNoticeProps) {
  const t = useTranslations("sprint.takeover");

  if (conflicts.length === 0) return null;

  // One line per series; a sprint shared by several boards gives several rows.
  const stoppingSeries = new Map<string, string>();
  for (const conflict of conflicts) {
    if (conflict.endsSeriesName && !stoppingSeries.has(conflict.endsSeriesName)) {
      stoppingSeries.set(conflict.endsSeriesName, conflict.sprintName);
    }
  }

  return (
    <InlineAlert
      variant="warning"
      icon={<AlertCircle />}
      description={
        <div className="space-y-1">
          <span className="inline-flex items-start gap-1.5">
            <span>{t("warning", { count: countSelectedBoards(conflicts), name: sprintName })}</span>
            <span className="mt-0.5 shrink-0">
              <InfoTip label={t("infoLabel")}>
                <ul className="space-y-1">
                  {conflicts.map((conflict) => (
                    <li
                      key={`${conflict.sprintId}:${conflict.boardId}`}
                      className="flex items-center gap-1.5"
                    >
                      {!conflict.canManage && <Lock className="size-3 shrink-0" aria-hidden />}
                      <span>
                        {conflict.boardName}: {conflict.sprintName}
                        {conflict.movesToBacklog && `, ${t("goesToBacklog")}`}
                        {!conflict.canManage && `, ${t("adminRequired")}`}
                      </span>
                    </li>
                  ))}
                </ul>
              </InfoTip>
            </span>
          </span>
          {stoppingSeries.size > 0 && (
            <ul className="text-foreground space-y-1">
              {[...stoppingSeries].map(([series, sprint]) => (
                <li key={series}>{t("seriesStops", { series, sprint })}</li>
              ))}
            </ul>
          )}
        </div>
      }
    />
  );
}
