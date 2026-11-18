import { useLocalSearchParams } from "expo-router";
import { View } from "react-native";

import { BottomSheet } from "@/components/kaizen/BottomSheet";
import { ListRow, ListSection } from "@/components/kaizen/List";
import { MissionRank } from "@/components/kaizen/TicketMeta";
import { Text } from "@/components/ui/text";
import { useTranslation, type TranslationKey } from "@/i18n";

/** The sections of Home that explain how they are counted. */
type InfoTopic = "points" | "burndown" | "stale";

const TITLE: Record<InfoTopic, TranslationKey> = {
  points: "homeLeaderboardInfo",
  burndown: "homeBurndownInfo",
  stale: "homeStaleInfo",
};

/**
 * Points per Mission Rank, as the server counts them: a finished ticket
 * earns its rank's points, split evenly between its assignees.
 */
const RANK_POINTS: readonly { rank: string; size: TranslationKey; points: number }[] = [
  { rank: "XS", size: "homeInfoRankXs", points: 1 },
  { rank: "S", size: "homeInfoRankS", points: 2 },
  { rank: "M", size: "homeInfoRankM", points: 3 },
  { rank: "L", size: "homeInfoRankL", points: 5 },
  { rank: "XL", size: "homeInfoRankXl", points: 8 },
  { rank: "XXL", size: "homeInfoRankXxl", points: 13 },
];

function isInfoTopic(value: unknown): value is InfoTopic {
  return value === "points" || value === "burndown" || value === "stale";
}

/** A Home info sheet: how the leaderboard, the burndown or the stale backlog is counted. */
export default function HomeInfoSheet() {
  const { t } = useTranslation();
  const params = useLocalSearchParams<{ topic: string }>();
  // An unknown topic from an old link falls back to the points rule.
  const topic: InfoTopic = isInfoTopic(params.topic) ? params.topic : "points";

  return (
    <BottomSheet title={t(TITLE[topic])} cancelLabel={t("homeClose")} scroll>
      <View className="gap-3 pb-8 pt-2">
        {topic === "points" ? <PointsInfo /> : null}
        {topic === "burndown" ? (
          <Paragraphs keys={["homeInfoBurndownBody", "homeInfoBurndownPace"]} />
        ) : null}
        {topic === "stale" ? (
          <Paragraphs keys={["homeInfoStaleBody", "homeInfoStaleDelete"]} />
        ) : null}
      </View>
    </BottomSheet>
  );
}

function Paragraphs({ keys }: { keys: TranslationKey[] }) {
  const { t } = useTranslation();
  return (
    <View className="gap-3 px-4">
      {keys.map((key) => (
        <Text key={key} variant="body">
          {t(key)}
        </Text>
      ))}
    </View>
  );
}

function PointsInfo() {
  const { t } = useTranslation();
  return (
    <>
      <Paragraphs keys={["homeInfoPointsBody"]} />
      <ListSection header={t("homeInfoPointsSection")} footer={t("homeInfoPointsFooter")}>
        {RANK_POINTS.map((row) => (
          <ListRow
            key={row.rank}
            leading={<MissionRank rank={row.rank} />}
            title={t(row.size)}
            value={t("homePoints", { count: row.points, points: row.points })}
          />
        ))}
      </ListSection>
    </>
  );
}
