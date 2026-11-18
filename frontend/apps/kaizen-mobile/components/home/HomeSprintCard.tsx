import { ChevronsUpDown } from "lucide-react-native";
import { Platform, Pressable, View } from "react-native";

import { labelColour } from "@/components/kaizen/ticketMetaModel";
import { sprintProgress, type SprintProgress } from "@/components/home/homeModel";
import { Icon } from "@/components/ui/icon";
import { ProgressBar } from "@/components/ui/progress";
import { Text } from "@/components/ui/text";
import { useDates } from "@/hooks/useDates";
import { useTranslation } from "@/i18n";
import type { HomeSprint } from "@/lib/schemas/home";
import { cn } from "@/lib/utils";

type HomeSprintCardProps = {
  sprint: HomeSprint;
  /** Opens the board chooser; set only when more than one board has an active sprint. */
  onChooseBoard?: () => void;
};

/**
 * The active sprint of the focus board: the name with the board beside it, the
 * days left, the dates and how much of the sprint has passed. A sprint past its
 * end that nobody completed reads "Ended N days ago" with a destructive bar.
 * When more than one board has an active sprint, the board box switches which
 * board's sprint the card and the burndown show.
 */
function HomeSprintCard({ sprint, onChooseBoard }: HomeSprintCardProps) {
  const { t } = useTranslation();
  const dates = useDates();
  const progress: SprintProgress = sprintProgress(sprint.startDate, sprint.endDate);
  const board = sprint.boards.find((candidate) => candidate.id === sprint.boardId);
  const dot: string | null = labelColour(board?.color);

  const boardBox = sprint.boardName ? (
    <View className="h-7 flex-row items-center gap-1.5 rounded-md border border-border pl-2.5 pr-2">
      <View
        className={cn("size-2 rounded-full", dot ? undefined : "bg-primary")}
        style={dot ? { backgroundColor: dot } : undefined}
      />
      <Text className="shrink font-sans-medium text-footnote text-foreground" numberOfLines={1}>
        {sprint.boardName}
      </Text>
      {onChooseBoard ? (
        <Icon as={ChevronsUpDown} size={14} className="text-muted-foreground" />
      ) : null}
    </View>
  ) : null;

  let counter: string = t("homeSprintNoEndDate");
  let range: string | null = null;
  if (progress.kind !== "no-end") {
    counter = dates.daysLeft(progress.daysLeft);
    range = progress.startDay
      ? dates.range(progress.startDay, progress.endDay)
      : dates.shortDate(progress.endDay);
  }

  return (
    <View className="gap-1.5 px-4">
      <View className="flex-row items-center gap-2">
        <Text numberOfLines={1} className="flex-1 font-sans-semibold text-callout text-foreground">
          {sprint.sprintName}
        </Text>
        {onChooseBoard && boardBox ? (
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={`${sprint.boardName ?? ""}, ${t("homeSprintChooseBoard")}`}
            onPress={onChooseBoard}
            // Grows the 28 pt box to the 44 pt (iOS) and 48 dp (Android) touch target.
            hitSlop={Platform.OS === "android" ? 10 : 8}
            className="max-w-[55%] active:opacity-60"
          >
            {boardBox}
          </Pressable>
        ) : boardBox ? (
          <View className="max-w-[55%]">{boardBox}</View>
        ) : null}
      </View>
      <Text
        variant="title-1"
        className={progress.kind === "ended" ? "text-destructive" : undefined}
      >
        {counter}
      </Text>
      {range ? (
        <Text variant="footnote" className="text-muted-foreground">
          {range}
        </Text>
      ) : null}
      {progress.kind !== "no-end" && progress.percent !== null ? (
        <ProgressBar
          label={t("homeSprintDaysPassed")}
          value={progress.percent}
          tone={progress.kind === "ended" ? "destructive" : "primary"}
          className="mt-1.5"
        />
      ) : null}
    </View>
  );
}

export { HomeSprintCard };
export type { HomeSprintCardProps };
