import type * as React from "react";
import { Pressable, View } from "react-native";

import {
  DashSection,
  DashSectionLink,
  SectionEmpty,
  SectionError,
  SectionSkeleton,
} from "@/components/home/DashSection";
import { daysUntilDeadline } from "@/components/home/homeModel";
import { useHomeLinks } from "@/components/home/homeNavigation";
import { HealthBadge } from "@/components/kaizen/TicketMeta";
import { labelColour } from "@/components/kaizen/ticketMetaModel";
import { Separator } from "@/components/ui/separator";
import { Text } from "@/components/ui/text";
import { useHomeDeadlines } from "@/hooks/queries/home";
import { useDates } from "@/hooks/useDates";
import { useTranslation } from "@/i18n";
import type { HomeDeadline } from "@/lib/schemas/home";
import { cn } from "@/lib/utils";

/**
 * Open milestones due in the next 60 days or already overdue, across the
 * user's boards. A row opens the milestone's board roadmap.
 */
function DeadlinesSection() {
  const { t } = useTranslation();
  const { openBoardPage } = useHomeLinks();
  const query = useHomeDeadlines();
  const first: HomeDeadline | undefined = query.data?.[0];

  let body: React.ReactNode;
  if (query.isPending) {
    body = <SectionSkeleton rows={3} />;
  } else if (query.isError && !query.data) {
    body = <SectionError message={t("homeDeadlinesError")} onRetry={() => void query.refetch()} />;
  } else if (!query.data || query.data.length === 0) {
    body = <SectionEmpty message={t("homeDeadlinesEmpty")} />;
  } else {
    body = (
      <View>
        {query.data.map((deadline, index) => (
          <View key={deadline.id}>
            {index > 0 ? <Separator className="mx-4" /> : null}
            <DeadlineRow
              deadline={deadline}
              onPress={() => openBoardPage(deadline.boardSlug, "roadmap")}
            />
          </View>
        ))}
      </View>
    );
  }

  return (
    <DashSection
      title={t("homeDeadlinesTitle")}
      accessory={
        first ? (
          <DashSectionLink
            label={t("homeDeadlinesRoadmap")}
            onPress={() => openBoardPage(first.boardSlug, "roadmap")}
          />
        ) : null
      }
    >
      {body}
    </DashSection>
  );
}

function DeadlineRow({ deadline, onPress }: { deadline: HomeDeadline; onPress: () => void }) {
  const { t } = useTranslation();
  const dates = useDates();
  const days: number | null = daysUntilDeadline(deadline.targetDate);
  const due: string | null = days === null ? null : dates.dueIn(days);
  const dot: string | null = labelColour(deadline.color);
  const meta: string = `${deadline.boardName} · ${t("homeDeadlinesProgress", {
    done: deadline.doneCount,
    total: deadline.totalCount,
  })}`;
  const day: string | null = dates.shortDate(deadline.targetDate);

  return (
    <Pressable
      accessibilityRole="link"
      accessibilityLabel={[day, deadline.name, meta, due].filter(Boolean).join(", ")}
      onPress={onPress}
      android_ripple={{ foreground: true }}
      className="min-h-16 flex-row items-center gap-3 px-4 py-2.5 active:bg-accent"
    >
      <View className="w-12">
        <Text variant="footnote" className="font-sans-semibold">
          {day ?? ""}
        </Text>
      </View>
      <View className="flex-1 gap-0.5">
        <View className="flex-row items-center gap-1.5">
          {dot ? <View className="size-2 rounded-full" style={{ backgroundColor: dot }} /> : null}
          <Text variant="body" numberOfLines={1} className="shrink">
            {deadline.name}
          </Text>
        </View>
        <Text variant="caption" className="text-muted-foreground" numberOfLines={1}>
          {meta}
        </Text>
      </View>
      <View className="items-end gap-1">
        <HealthBadge health={deadline.health} />
        {due ? (
          <Text
            variant="caption"
            className={cn(days !== null && days < 0 ? "text-destructive" : "text-muted-foreground")}
          >
            {due}
          </Text>
        ) : null}
      </View>
    </Pressable>
  );
}

export { DeadlinesSection };
