import { format } from "date-fns";
import { enUS, nl, tr } from "date-fns/locale";
import { ChevronLeft, ChevronRight, Flag, Folder } from "lucide-react-native";
import * as React from "react";
import { ScrollView, View, type RefreshControlProps } from "react-native";

import { quarterTodayPercent, type Quarter } from "@/components/boards/boardModel";
import { useBoardLinks } from "@/components/boards/boardNavigation";
import { RoadmapRow } from "@/components/boards/RoadmapRow";
import { EmptyState } from "@/components/kaizen/EmptyState";
import { Chip } from "@/components/ui/chip";
import { Icon } from "@/components/ui/icon";
import { IconButton } from "@/components/ui/icon-button";
import { SkeletonGroup, SkeletonRow } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { useRoadmap } from "@/hooks/queries/boards";
import { useTranslation, type SupportedLanguage } from "@/i18n";
import { parseDateOnly, todayDateOnly } from "@/lib/dates";
import { hapticSelection } from "@/lib/haptics";
import type { BoardListItem, RoadmapLane } from "@/lib/schemas/boards";

const MONTH_LOCALES = { en: enUS, nl, tr } as const;

/** "Q4 2026", the quarter a roadmap shows. */
export function useQuarterLabel(): (quarter: Quarter) => string {
  const { t } = useTranslation();
  return React.useCallback(
    (quarter: Quarter) =>
      t("boardRoadmapQuarter", { quarter: quarter.quarter, year: quarter.year }),
    [t]
  );
}

type RoadmapViewProps = {
  board: BoardListItem;
  quarter: Quarter;
  /** Moves the shown quarter by one, back or forward. */
  onShiftQuarter: (step: -1 | 1) => void;
  renderRefreshControl: () => React.ReactElement<RefreshControlProps>;
};

/**
 * The Roadmap view: the milestones of one quarter, one lane per board (the
 * board, then its child boards), each milestone placed on the quarter with a
 * marker at today. A milestone carries no project, so the lanes, not projects,
 * group and filter the roadmap. A milestone opens in Kaizen web, where its
 * tickets are listed.
 */
function RoadmapView({ board, quarter, onShiftQuarter, renderRefreshControl }: RoadmapViewProps) {
  const { t, language } = useTranslation();
  const quarterLabel = useQuarterLabel();
  const { openWebBoardPage } = useBoardLinks();
  const roadmap = useRoadmap(board.id, quarter);
  const [laneId, setLaneId] = React.useState<string | null>(null);

  const lanes: RoadmapLane[] = (roadmap.data?.boards ?? []).filter(
    (lane) => lane.milestones.length > 0
  );
  const shown: RoadmapLane[] = laneId ? lanes.filter((lane) => lane.id === laneId) : lanes;
  const today: number | null = quarterTodayPercent(quarter, todayDateOnly());
  const locale = MONTH_LOCALES[language as SupportedLanguage] ?? enUS;
  const months: string[] = quarter.months.map((month) => {
    const day: Date | null = parseDateOnly(month);
    return day ? format(day, "MMM", { locale }).toUpperCase() : "";
  });

  let body: React.ReactNode;
  if (roadmap.isPending) {
    body = (
      <SkeletonGroup>
        {[0, 1, 2].map((index) => (
          <SkeletonRow key={index} />
        ))}
      </SkeletonGroup>
    );
  } else if (roadmap.isError) {
    body = (
      <EmptyState
        variant="error"
        title={t("boardRoadmapError")}
        onAction={() => void roadmap.refetch()}
        className="mt-6"
      />
    );
  } else if (shown.length === 0) {
    body = <EmptyState icon={Flag} title={t("boardRoadmapEmpty")} className="mt-6" />;
  } else {
    body = shown.map((lane) => (
      <View key={lane.id} className="pt-3">
        <View className="flex-row items-center gap-2 px-4 pb-1">
          <Icon as={Folder} size={14} className="text-muted-foreground" />
          <Text className="font-sans-semibold text-footnote uppercase text-muted-foreground">
            {lane.prefix ? `${lane.name} · ${lane.prefix}` : lane.name}
          </Text>
        </View>
        {lane.milestones.map((milestone) => (
          <RoadmapRow
            key={milestone.id}
            milestone={milestone}
            from={quarter.from}
            to={quarter.to}
            todayPercent={today}
            onPress={() => openWebBoardPage(board.slug, "roadmap")}
          />
        ))}
      </View>
    ));
  }

  return (
    <ScrollView
      className="flex-1"
      contentInsetAdjustmentBehavior="automatic"
      contentContainerClassName="pb-10"
      refreshControl={renderRefreshControl()}
    >
      <View className="flex-row items-center justify-between px-2 pt-2">
        <IconButton
          icon={ChevronLeft}
          label={t("boardRoadmapPrevious")}
          onPress={() => onShiftQuarter(-1)}
        />
        <Text variant="headline">{quarterLabel(quarter)}</Text>
        <IconButton
          icon={ChevronRight}
          label={t("boardRoadmapNext")}
          onPress={() => onShiftQuarter(1)}
        />
      </View>
      {lanes.length > 1 ? (
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerClassName="gap-2 px-4 py-2"
          className="grow-0"
        >
          <Chip
            label={t("boardRoadmapAllBoards")}
            selected={laneId === null}
            onPress={() => {
              hapticSelection();
              setLaneId(null);
            }}
          />
          {lanes.map((lane) => (
            <Chip
              key={lane.id}
              label={lane.name}
              selected={laneId === lane.id}
              onPress={() => {
                hapticSelection();
                setLaneId(laneId === lane.id ? null : lane.id);
              }}
            />
          ))}
        </ScrollView>
      ) : null}
      <View className="flex-row px-4 pt-2">
        {months.map((month, index) => (
          <Text key={index} variant="caption" className="flex-1 text-muted-foreground">
            {month}
          </Text>
        ))}
      </View>
      {body}
      <Text variant="footnote" className="px-4 pt-4 text-muted-foreground">
        {t("boardRoadmapHint")}
      </Text>
    </ScrollView>
  );
}

export { RoadmapView };
export type { RoadmapViewProps };
