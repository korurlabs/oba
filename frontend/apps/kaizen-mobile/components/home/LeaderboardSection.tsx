import { useRouter } from "expo-router";
import type * as React from "react";
import { View } from "react-native";

import {
  DashSection,
  SectionEmpty,
  SectionError,
  SectionSkeleton,
} from "@/components/home/DashSection";
import { PickerChip, type PickerOption } from "@/components/home/PickerChip";
import {
  formatPoints,
  hslWithAlpha,
  leaderboardRows,
  type LeaderboardRowModel,
} from "@/components/home/homeModel";
import { Avatar } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import { Text } from "@/components/ui/text";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useHomeLeaderboard } from "@/hooks/queries/home";
import { useTranslation } from "@/i18n";
import { HomeLeaderboardPeriod } from "@/lib/schemas/home";
import { cn } from "@/lib/utils";

/**
 * The first two medal places get a gold and a silver rank disc; the third
 * gets a bronze one from the P2 orange (see LeaderboardRow), because no tint
 * token is orange and the waiting tint is the same yellow as gold.
 */
const MEDAL_CLASS: Record<number, string> = {
  1: "bg-warning-light",
  2: "bg-muted",
};

const BRONZE_ALPHA = 0.15;

type LeaderboardSectionProps = {
  period: HomeLeaderboardPeriod;
  onPeriodChange: (period: HomeLeaderboardPeriod) => void;
  meId: string | undefined;
};

/**
 * Who finished the most Mission Rank points in the active sprint or the last
 * 30 days. The current user's row is always shown and tinted, after a divider
 * below the top five when that is where they stand.
 */
function LeaderboardSection({ period, onPeriodChange, meId }: LeaderboardSectionProps) {
  const { t, language } = useTranslation();
  const router = useRouter();
  const query = useHomeLeaderboard(period);

  const options: PickerOption<HomeLeaderboardPeriod>[] = [
    { value: HomeLeaderboardPeriod.SPRINT, label: t("homeLeaderboardSprint") },
    { value: HomeLeaderboardPeriod.LAST_30_DAYS, label: t("homeLeaderboardLast30") },
  ];

  let body: React.ReactNode;
  if (query.isPending) {
    body = <SectionSkeleton rows={4} />;
  } else if (query.isError && !query.data) {
    body = (
      <SectionError message={t("homeLeaderboardError")} onRetry={() => void query.refetch()} />
    );
  } else {
    const rows: LeaderboardRowModel[] = leaderboardRows(query.data?.entries ?? [], meId);
    body =
      rows.length === 0 ? (
        <SectionEmpty message={t("homeLeaderboardEmpty")} />
      ) : (
        <View>
          {rows.map((row, index) => (
            <View key={row.entry.userId}>
              {/* The reader's own row below the top five gets a full-width
                  divider; rows inside the list keep the inset one. */}
              {index > 0 ? <Separator className={row.detached ? "mx-4" : "ml-16 mr-4"} /> : null}
              <LeaderboardRow row={row} language={language} />
            </View>
          ))}
        </View>
      );
  }

  return (
    <DashSection
      title={t("homeLeaderboardTitle")}
      onInfo={() => router.push({ pathname: "/home/info/[topic]", params: { topic: "points" } })}
      infoLabel={t("homeLeaderboardInfo")}
      accessory={
        <PickerChip
          title={t("homeLeaderboardPeriod")}
          options={options}
          // The server counts the last 30 days when no member board has an active sprint.
          value={query.data?.period ?? period}
          onChange={onPeriodChange}
        />
      }
    >
      {body}
    </DashSection>
  );
}

function LeaderboardRow({ row, language }: { row: LeaderboardRowModel; language: string }) {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const points: string = formatPoints(row.entry.points, language);
  const pointsLabel: string = t("homePoints", { count: row.entry.points, points });
  const ticketsLabel: string = t("homeTickets", { count: row.entry.tickets });

  return (
    <View
      className={cn(
        "min-h-14 flex-row items-center gap-3 px-4 py-2",
        row.isMe && "bg-primary-light"
      )}
      accessible
      accessibilityLabel={[
        t("homeLeaderboardRank", { rank: row.rank }),
        row.name,
        row.isMe ? t("homeLeaderboardYou") : null,
        pointsLabel,
        ticketsLabel,
      ]
        .filter(Boolean)
        .join(", ")}
    >
      <View
        className={cn(
          "size-7 items-center justify-center rounded-full",
          MEDAL_CLASS[row.rank] ?? "bg-transparent"
        )}
        style={
          row.rank === 3 ? { backgroundColor: hslWithAlpha(colors.priorityP2, BRONZE_ALPHA) } : null
        }
      >
        <Text variant="subhead" className="font-sans-semibold">
          {row.rank}
        </Text>
      </View>
      <Avatar name={row.name} size="md" />
      <View className="flex-1 flex-row items-center gap-2">
        <Text variant="body" numberOfLines={1} className="shrink">
          {row.name}
        </Text>
        {row.isMe ? (
          <Badge variant="tag" className="bg-primary-light" textClassName="text-primary-fill">
            {t("homeLeaderboardYou")}
          </Badge>
        ) : null}
      </View>
      <View className="items-end">
        <Text variant="subhead" className="font-sans-semibold">
          {pointsLabel}
        </Text>
        <Text variant="caption" className="text-muted-foreground">
          {ticketsLabel}
        </Text>
      </View>
    </View>
  );
}

export { LeaderboardSection };
export type { LeaderboardSectionProps };
