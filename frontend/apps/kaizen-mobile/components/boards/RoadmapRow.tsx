import * as React from "react";
import { Pressable, View } from "react-native";

import { milestoneBar, milestoneProgress } from "@/components/boards/boardModel";
import { HealthBadge } from "@/components/kaizen/TicketMeta";
import { Text } from "@/components/ui/text";
import { useDates } from "@/hooks/useDates";
import { useTranslation } from "@/i18n";
import type { Milestone } from "@/lib/schemas/boards";

type RoadmapRowProps = {
  milestone: Milestone;
  /** The quarter shown, `YYYY-MM-DD`. */
  from: string;
  to: string;
  /** Today's place in the quarter, 0 to 100, or null outside it. */
  todayPercent: number | null;
  onPress: () => void;
};

/**
 * One milestone of the roadmap: name and health, a bar placed and sized in
 * the quarter with the done share filled, the date range and done of total
 * tickets.
 */
function RoadmapRow({ milestone, from, to, todayPercent, onPress }: RoadmapRowProps) {
  const { t } = useTranslation();
  const dates = useDates();
  const bar = milestoneBar(milestone, from, to);
  const progress: number = milestoneProgress(milestone);
  // The roadmap read carries no ticket counts; only a milestone with them shows progress.
  const counted: boolean = milestone.totalCount != null;
  const range: string | null =
    milestone.startDate && milestone.targetDate
      ? dates.range(milestone.startDate, milestone.targetDate)
      : milestone.targetDate
        ? dates.shortDate(milestone.targetDate)
        : null;

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={[
        milestone.name,
        range,
        counted ? t("boardRoadmapProgress", { percent: progress }) : null,
      ]
        .filter(Boolean)
        .join(", ")}
      onPress={onPress}
      className="gap-2 px-4 py-3 active:opacity-70"
    >
      <View className="flex-row items-center gap-2">
        <Text variant="subhead" className="shrink" numberOfLines={1}>
          {milestone.name}
        </Text>
        <HealthBadge health={milestone.health} />
      </View>
      <View className="h-2 overflow-hidden rounded-full bg-muted">
        {bar ? (
          <View
            className="absolute h-2 overflow-hidden rounded-full bg-primary-light"
            style={{ left: `${bar.start}%`, width: `${bar.width}%` }}
          >
            <View className="h-2 bg-primary" style={{ width: `${progress}%` }} />
          </View>
        ) : null}
        {todayPercent !== null ? (
          <View
            className="absolute h-2 w-0.5 bg-destructive"
            style={{ left: `${todayPercent}%` }}
          />
        ) : null}
      </View>
      <View className="flex-row items-center justify-between">
        <Text variant="caption" className="text-muted-foreground">
          {range ?? t("boardRoadmapNoDates")}
        </Text>
        {counted ? (
          <Text variant="caption" className="text-muted-foreground">
            {t("boardRoadmapDone", {
              done: milestone.doneCount ?? 0,
              total: milestone.totalCount ?? 0,
              percent: progress,
            })}
          </Text>
        ) : null}
      </View>
    </Pressable>
  );
}

export { RoadmapRow };
export type { RoadmapRowProps };
