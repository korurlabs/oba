"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import { Loader2 } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { BoardDot } from "@/components/ui/board-dot";
import { Separator } from "@/components/ui/separator";
import { cn } from "@/lib/utils";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { useBoards, BOARD_TREE_PARAMS } from "@/features/boards/hooks/use-boards";
import type { BoardListWithChildrenResponse } from "@/features/boards/schemas";
import { useSprint } from "../hooks/use-sprints";
import type { SprintDetailResponse } from "../schemas";
import { seriesRunMessage } from "../lib/series-run-message";

interface SprintBoardNode {
  id: string;
  name: string;
  color?: string;
  depth: number;
  joins: boolean;
}

/**
 * The boards of the sprint plus the boards that join it, in the order and
 * depth of the board tree. A board whose parent is in neither set moves up to
 * the depth of its nearest shown ancestor, so the tree stays connected.
 */
function sprintBoardRows(
  tree: readonly BoardListWithChildrenResponse[],
  sprintBoardIds: ReadonlySet<string>,
  joiningIds: ReadonlySet<string>
): SprintBoardNode[] {
  const rows: SprintBoardNode[] = [];
  const walk = (boards: readonly BoardListWithChildrenResponse[], depth: number) => {
    for (const board of boards) {
      const shown = sprintBoardIds.has(board.id) || joiningIds.has(board.id);
      if (shown) {
        rows.push({
          id: board.id,
          name: board.name,
          color: board.color,
          depth,
          joins: joiningIds.has(board.id),
        });
      }
      walk((board.childBoards ?? []) as BoardListWithChildrenResponse[], shown ? depth + 1 : depth);
    }
  };
  walk(tree, 0);
  return rows;
}

/**
 * The active sprint of the board picked in the join dialog: its dates, goal
 * and repetition, and every board in it as a tree, with the boards that join
 * marked.
 */
export function ParentSprintDetails({
  parentBoardId,
  sprintId,
  boardName,
  joiningBoardIds,
}: {
  parentBoardId: string;
  sprintId: string;
  /** Name of the board the dialog was opened for. */
  boardName: string;
  /** The boards the sprint is linked to on join, this board among them. */
  joiningBoardIds: string[];
}) {
  const t = useTranslations("sprint");
  const { data, isLoading, isError } = useSprint(parentBoardId, sprintId);
  const sprint = data?.data;

  return (
    <div className="border-border rounded-lg border text-sm">
      {isLoading ? (
        <div className="flex justify-center p-4">
          <Loader2 className="text-muted-foreground h-4 w-4 animate-spin" />
        </div>
      ) : isError || !sprint ? (
        <p className="text-destructive p-3">{t("joinParent.detailsLoadFailed")}</p>
      ) : (
        <SprintDetailsBody
          sprint={sprint}
          boardName={boardName}
          joiningBoardIds={joiningBoardIds}
        />
      )}
    </div>
  );
}

function SprintDetailsBody({
  sprint,
  boardName,
  joiningBoardIds,
}: {
  sprint: SprintDetailResponse;
  boardName: string;
  joiningBoardIds: string[];
}) {
  const t = useTranslations("sprint");
  const tReasons = useTranslations("sprint.recurrence.reasons");
  const { formatDate } = useDateFormat();
  const { data: treeResponse } = useBoards(BOARD_TREE_PARAMS);

  const rows = useMemo(() => {
    const tree = (treeResponse?.data ?? []) as BoardListWithChildrenResponse[];
    const sprintBoardIds = new Set(sprint.boards.map((board) => board.id));
    const joiningIds = new Set(joiningBoardIds);
    const treeRows = sprintBoardRows(tree, sprintBoardIds, joiningIds);
    // A sprint board missing from the tree (beyond its page) is still listed.
    const inTree = new Set(treeRows.map((row) => row.id));
    const rest = sprint.boards
      .filter((board) => !inTree.has(board.id))
      .map((board) => ({
        id: board.id,
        name: board.name,
        color: board.color,
        depth: 0,
        joins: false,
      }));
    return [...treeRows, ...rest];
  }, [treeResponse?.data, sprint.boards, joiningBoardIds]);

  const series = sprint.series ?? null;
  // A switched-off series records why it stopped, e.g. a board in another series.
  const pausedReason =
    series && !series.enabled && series.lastRunStatus === "FAILED" && series.lastRunMessage
      ? seriesRunMessage(series.lastRunMessage, tReasons)
      : null;

  const repeatValue = !series
    ? t("joinParent.repeatNone")
    : !series.enabled
      ? t("joinParent.repeatOffShort", { series: series.name })
      : series.lengthDays % 7 === 0
        ? t("joinParent.repeatEveryWeeks", { weeks: series.lengthDays / 7 })
        : t("joinParent.repeatEveryDays", { days: series.lengthDays });
  const repeatEffect = !series
    ? t("joinParent.once", { board: boardName })
    : !series.enabled
      ? t("joinParent.repeatOff", { board: boardName })
      : t("joinParent.repeats", { board: boardName });

  return (
    <>
      <div className="flex items-center justify-between gap-2 p-3">
        <span className="text-foreground font-semibold">{sprint.name}</span>
        <Badge variant="secondary">{t("active")}</Badge>
      </div>
      <Separator />
      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 p-3">
        {sprint.startDate && sprint.endDate && (
          <>
            <dt className="text-muted-foreground">{t("joinParent.dates")}</dt>
            <dd className="text-foreground">
              {formatDate(sprint.startDate)} – {formatDate(sprint.endDate)}
            </dd>
          </>
        )}
        {sprint.goal && (
          <>
            <dt className="text-muted-foreground">{t("joinParent.goal")}</dt>
            <dd className="text-foreground">{sprint.goal}</dd>
          </>
        )}
        <dt className="text-muted-foreground">{t("joinParent.repeat")}</dt>
        <dd className="text-foreground flex flex-col gap-1">
          <span>{repeatValue}</span>
          <span className="text-muted-foreground text-xs">{repeatEffect}</span>
          {pausedReason && (
            <span className="text-muted-foreground text-xs">
              {t("joinParent.repeatOffReason", { reason: pausedReason })}
            </span>
          )}
        </dd>
      </dl>
      <Separator />
      <div className="flex flex-col gap-1 p-3">
        <span className="text-muted-foreground mb-1">
          {t("joinParent.boardsInSprint", { count: sprint.boards.length })}
        </span>
        <ul className="flex flex-col">
          {rows.map((row) => (
            <li
              key={row.id}
              className={cn(
                "flex items-center gap-1 rounded py-0.5 pr-1",
                row.joins && "bg-primary/10"
              )}
              style={{ paddingLeft: `${row.depth * 16}px` }}
            >
              <BoardDot color={row.color} />
              <span className={cn("text-foreground truncate", row.joins && "font-medium")}>
                {row.name}
              </span>
              {row.joins && (
                <Badge variant="default" className="ml-auto shrink-0">
                  {t("joinParent.joins")}
                </Badge>
              )}
            </li>
          ))}
        </ul>
      </div>
    </>
  );
}
