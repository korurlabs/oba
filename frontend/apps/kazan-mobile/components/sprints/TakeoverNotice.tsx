import { TriangleAlert } from "lucide-react-native";
import * as React from "react";
import { View } from "react-native";

import {
  selectedConflictBoards,
  stoppingSeries,
  type StoppingSeries,
  type TakeoverConflict,
} from "@/components/sprints/sprintModel";
import { Card } from "@/components/ui/card";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { useTranslation } from "@/i18n";

/** One line per blocking board: "Board: Sprint", with where its tickets go and who may close it. */
export function useConflictLines(): (conflicts: readonly TakeoverConflict[]) => string[] {
  const { t } = useTranslation();
  return React.useCallback(
    (conflicts: readonly TakeoverConflict[]) =>
      conflicts.map((conflict) =>
        [
          `${conflict.boardName}: ${conflict.sprintName}`,
          conflict.movesToBacklog ? t("sprintTakeoverToBacklog") : null,
          conflict.canManage ? null : t("sprintAdminRequired"),
        ]
          .filter(Boolean)
          .join(", ")
      ),
    [t]
  );
}

/**
 * One line per repeating series the takeover stops. Closing a series' last
 * running sprint ends its repeat, which nothing else in the dialog shows.
 */
export function useSeriesStopLines(): (conflicts: readonly TakeoverConflict[]) => string[] {
  const { t } = useTranslation();
  return React.useCallback(
    (conflicts: readonly TakeoverConflict[]) =>
      stoppingSeries(conflicts).map((stop: StoppingSeries) =>
        t("sprintTakeoverSeriesStops", { series: stop.series, sprint: stop.sprint })
      ),
    [t]
  );
}

type TakeoverNoticeProps = {
  conflicts: readonly TakeoverConflict[];
  /** The sprint that takes over. */
  sprintName: string;
};

/**
 * Warns that boards of a new sprint already run an active sprint, which it
 * can only take over by closing it, lists each of those sprints and names
 * each repeating series that stops with them.
 */
function TakeoverNotice({ conflicts, sprintName }: TakeoverNoticeProps) {
  const { t } = useTranslation();
  const lines = useConflictLines();
  const seriesStopLines = useSeriesStopLines();
  if (conflicts.length === 0) return null;
  return (
    <Card className="mx-4 mt-6 gap-2">
      <View className="flex-row items-start gap-2">
        <Icon as={TriangleAlert} size={16} className="mt-0.5 text-warning" />
        <Text variant="subhead" className="flex-1">
          {t("sprintTakeoverWarning", {
            count: selectedConflictBoards(conflicts),
            name: sprintName,
          })}
        </Text>
      </View>
      {lines(conflicts).map((line, index) => (
        <Text
          key={`${conflicts[index]?.sprintId}:${conflicts[index]?.boardId}`}
          variant="footnote"
          className="pl-6 text-muted-foreground"
        >
          {line}
        </Text>
      ))}
      {seriesStopLines(conflicts).map((line) => (
        <Text key={line} variant="footnote" className="pl-6">
          {line}
        </Text>
      ))}
    </Card>
  );
}

export { TakeoverNotice };
