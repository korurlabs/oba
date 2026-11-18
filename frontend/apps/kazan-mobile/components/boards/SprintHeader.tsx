import { Calendar, Ellipsis, EllipsisVertical } from "lucide-react-native";
import * as React from "react";
import { Platform, View } from "react-native";

import { sprintProgressSegments } from "@/components/boards/boardModel";
import { sprintProgress, type SprintProgress } from "@/components/home/homeModel";
import { resolveStatus, type TicketStatus } from "@/components/kazan/ticketMetaModel";
import { Card } from "@/components/ui/card";
import { Icon } from "@/components/ui/icon";
import { IconButton } from "@/components/ui/icon-button";
import { Text } from "@/components/ui/text";
import { useSprintDetail } from "@/hooks/queries/sprints";
import { useDates } from "@/hooks/useDates";
import { useTranslation, type TranslationKey } from "@/i18n";
import type { BoardTicket, Sprint } from "@/lib/schemas/boards";
import { cn } from "@/lib/utils";

const SPRINT_STATUS_KEY: Record<string, TranslationKey> = {
  ACTIVE: "boardSprintActive",
  PLANNED: "boardSprintPlanned",
  COMPLETED: "boardSprintCompleted",
};

/** A sprint's state as the board writes it: "Active", "Planned" or "Completed". */
export function useSprintStatusLabel(): (status: string) => string {
  const { t } = useTranslation();
  return React.useCallback(
    (status: string) => {
      const key: TranslationKey | undefined = SPRINT_STATUS_KEY[status];
      return key ? t(key) : status;
    },
    [t]
  );
}

/**
 * A sprint's date range and days counter. Sprint days follow the Istanbul
 * business calendar, as on Home, so both screens give the same count.
 */
export function useSprintDates(sprint: Sprint | null): {
  range: string | null;
  left: string | null;
} {
  const dates = useDates();
  if (!sprint) return { range: null, left: null };
  const progress: SprintProgress = sprintProgress(sprint.startDate, sprint.endDate);
  const range: string | null =
    progress.startDay && progress.kind !== "no-end"
      ? dates.range(progress.startDay, progress.endDay)
      : null;
  const left: string | null =
    progress.kind !== "no-end" && sprint.status !== "COMPLETED"
      ? dates.daysLeft(progress.daysLeft)
      : null;
  return { range, left };
}

const SPRINT_TAG_CLASS: Record<string, string> = {
  ACTIVE: "bg-status-bg-in-progress",
  PLANNED: "bg-status-bg-todo",
  COMPLETED: "bg-status-bg-done",
};

const LEGEND_KEY: Record<TicketStatus, TranslationKey> = {
  DONE: "boardLegendDone",
  IN_REVIEW: "boardLegendReview",
  WAITING_FOR_THIRD_PARTY: "boardLegendWaiting",
  IN_PROGRESS: "boardLegendProgress",
  TODO: "boardLegendTodo",
};

type SprintHeaderProps = {
  sprint: Sprint;
  /** The board of the sprint, to read its goal. */
  boardId: string;
  tickets: readonly BoardTicket[];
  /** Opens the sprint's actions; without it the header has no actions button. */
  onMore?: () => void;
};

/**
 * The sprint's name and state, its dates and days left, its goal, and its
 * tickets as a bar per status, furthest along first, with a legend and the
 * share done. The goal comes from the sprint detail, which the list of
 * sprints does not carry; while it loads, or if it fails, the header shows
 * the rest without it.
 */
function SprintHeader({ sprint, boardId, tickets, onMore }: SprintHeaderProps) {
  const { t } = useTranslation();
  const statusLabel = useSprintStatusLabel();
  const { range, left } = useSprintDates(sprint);
  const goal: string | null | undefined = useSprintDetail(boardId, sprint.id).data?.goal;
  const { segments, donePercent } = sprintProgressSegments(tickets);
  const total: number = tickets.length;

  return (
    <Card className="mx-4 gap-2.5">
      <View className="flex-row items-center gap-2">
        <Text className="shrink font-sans-semibold text-title-3" numberOfLines={1}>
          {sprint.name}
        </Text>
        <View
          className={cn(
            "rounded px-1.5 py-0.5",
            SPRINT_TAG_CLASS[sprint.status] ?? "bg-status-bg-todo"
          )}
        >
          <Text variant="badge" className="text-foreground">
            {statusLabel(sprint.status)}
          </Text>
        </View>
        <View className="flex-1" />
        {onMore ? (
          <IconButton
            icon={Platform.OS === "ios" ? Ellipsis : EllipsisVertical}
            label={t("boardSprintActions")}
            onPress={onMore}
            className="-my-2 -mr-2"
          />
        ) : null}
      </View>
      {range || left ? (
        <View className="flex-row items-center gap-1.5">
          <Icon as={Calendar} size={13} className="text-muted-foreground" />
          <Text variant="footnote" className="shrink text-muted-foreground" numberOfLines={1}>
            {[range, left].filter(Boolean).join(" · ")}
          </Text>
        </View>
      ) : null}
      {goal ? (
        <Text variant="footnote" className="text-foreground" numberOfLines={2}>
          {goal}
        </Text>
      ) : null}
      <View
        accessible
        accessibilityRole="progressbar"
        accessibilityLabel={t("boardRoadmapProgress", { percent: donePercent })}
        className="h-1.5 flex-row gap-0.5 overflow-hidden rounded-full bg-muted"
      >
        {total > 0
          ? segments
              .filter((segment) => segment.count > 0)
              .map((segment) => (
                <View
                  key={segment.status}
                  style={{ flex: segment.count }}
                  className={resolveStatus(segment.status).dotClassName}
                />
              ))
          : null}
      </View>
      <View className="flex-row items-start gap-3">
        <View className="flex-1 flex-row flex-wrap gap-x-3 gap-y-1">
          {segments.map((segment) => (
            <View key={segment.status} className="flex-row items-center gap-1">
              <View
                className={cn("size-2 rounded-full", resolveStatus(segment.status).dotClassName)}
              />
              <Text variant="caption" className="text-muted-foreground">
                {`${t(LEGEND_KEY[segment.status])} ${segment.count}`}
              </Text>
            </View>
          ))}
        </View>
        <Text variant="caption" className="font-sans-semibold text-foreground">
          {`${donePercent}%`}
        </Text>
      </View>
    </Card>
  );
}

export { SprintHeader };
export type { SprintHeaderProps };
