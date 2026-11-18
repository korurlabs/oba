import { useIsFocused, useRouter } from "expo-router";
import { CircleCheck, Siren, UserRound } from "lucide-react-native";
import * as React from "react";
import { Platform, RefreshControl, SectionList, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { canWriteTickets } from "@/components/home/homeModel";
import { PickerChip, type PickerOption } from "@/components/home/PickerChip";
import {
  assignedTo,
  bannerIncident,
  groupIncidentsByPriority,
  INCIDENT_SEGMENTS,
  NO_PRIORITY,
  openP1Incidents,
  scopeOfSegment,
  type IncidentGroup,
  type IncidentSegment,
} from "@/components/incidents/incidentModel";
import { IncidentRow } from "@/components/incidents/IncidentRow";
import { EmptyState } from "@/components/kaizen/EmptyState";
import { Fab, FAB_LIST_BOTTOM_PADDING } from "@/components/kaizen/Fab";
import { IncidentBanner } from "@/components/kaizen/IncidentBanner";
import { OfflineBanner } from "@/components/kaizen/OfflineBanner";
import { TAB_ROOT_CONTENT_TOP, TabRootHeader } from "@/components/kaizen/TabRootHeader";
import { PriorityIcon } from "@/components/kaizen/TicketMeta";
import { resolvePriority } from "@/components/kaizen/ticketMetaModel";
import { ticketHref, newTicketHref } from "@/components/ticket/ticketRoutes";
import { Button } from "@/components/ui/button";
import { SegmentedControl } from "@/components/ui/segmented-control";
import { SkeletonCard } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useBoards } from "@/hooks/queries/boards";
import { useIncidents } from "@/hooks/queries/incidents";
import { useMe } from "@/hooks/queries/users";
import { useDates } from "@/hooks/useDates";
import { useOrganizationRole } from "@/hooks/useOrganizationRole";
import { useTranslation, type TranslationKey } from "@/i18n";
import type { TicketListItem } from "@/lib/schemas/tickets";
import { cn } from "@/lib/utils";

/** The board filter's value for every board; a board id is never this string. */
const ALL_BOARDS = "all";

/** How often the open durations are redrawn while the tab is on screen. */
const DURATION_TICK_MS = 60_000;

const SEGMENT_LABEL: Record<IncidentSegment, TranslationKey> = {
  open: "incidentsSegmentOpen",
  mine: "incidentsSegmentMine",
  resolved: "incidentsSegmentResolved",
};

type Section = { priority: string; data: TicketListItem[] };

/** A counter that rises every minute while `active`, so open durations stay current. */
function useMinuteTick(active: boolean): number {
  const [tick, setTick] = React.useState<number>(0);
  React.useEffect(() => {
    if (!active) return undefined;
    const id: ReturnType<typeof setInterval> = setInterval(
      () => setTick((value) => value + 1),
      DURATION_TICK_MS
    );
    return () => clearInterval(id);
  }, [active]);
  return tick;
}

/**
 * The Incidents tab: open incidents, the open ones assigned to me and the
 * resolved ones, grouped by priority with how long each has been open. The
 * banner names the open P1 incident that has waited longest, on every board
 * whatever the board filter, because a P1 elsewhere still needs someone.
 * Report incident opens New ticket as a P1 incident on the filtered board.
 */
export function IncidentsScreen() {
  const { t } = useTranslation();
  const router = useRouter();
  const colors = useThemeColors();
  const dates = useDates();
  const focused: boolean = useIsFocused();
  const tick: number = useMinuteTick(focused);
  const role: string | null = useOrganizationRole();
  const meId: string | undefined = useMe(true).data?.id;
  const boards = useBoards();

  const [segment, setSegment] = React.useState<IncidentSegment>("open");
  const [boardId, setBoardId] = React.useState<string | null>(null);
  const [refreshing, setRefreshing] = React.useState<boolean>(false);

  const resolvedView: boolean = scopeOfSegment(segment) === "resolved";
  const everyOpen = useIncidents("open", null);
  // Without a board filter this is the same query as `everyOpen`.
  const open = useIncidents("open", boardId);
  const resolved = useIncidents("resolved", boardId, resolvedView);
  const list = resolvedView ? resolved : open;

  const shown: TicketListItem[] = React.useMemo(() => {
    const incidents: TicketListItem[] = list.data ?? [];
    return segment === "mine" ? assignedTo(incidents, meId) : incidents;
  }, [list.data, segment, meId]);
  const sections: Section[] = React.useMemo(
    () =>
      groupIncidentsByPriority(shown).map((group: IncidentGroup) => ({
        priority: group.priority,
        data: group.incidents,
      })),
    [shown]
  );

  const banner: TicketListItem | null = bannerIncident(everyOpen.data ?? []);
  const p1Count: number = openP1Incidents(everyOpen.data ?? []).length;
  const bannerOpenFor: string | null = banner ? dates.openDuration(banner.createdAt) : null;

  const canReport: boolean = canWriteTickets(role);
  const report = React.useCallback(
    () => router.push(newTicketHref({ type: "INCIDENT", priority: "P1", boardId })),
    [router, boardId]
  );
  const openIncident = React.useCallback((key: string) => router.push(ticketHref(key)), [router]);

  const refresh = React.useCallback(() => {
    setRefreshing(true);
    Promise.all([list.refetch(), everyOpen.refetch()])
      .catch((error: unknown) => {
        // The list shows the failure; this only records the pull.
        console.warn("[incidents] refresh failed", error instanceof Error ? error.message : error);
      })
      .finally(() => setRefreshing(false));
  }, [list, everyOpen]);

  const boardOptions: PickerOption<string>[] = [
    { value: ALL_BOARDS, label: t("incidentsAllBoards") },
    ...(boards.data?.boards ?? []).map((board) => ({ value: board.id, label: board.name })),
  ];

  const header = (
    <View className={cn("gap-3 px-4 pb-3", TAB_ROOT_CONTENT_TOP)}>
      {banner ? (
        <IncidentBanner
          title={`${banner.key} ${banner.title}`}
          message={[
            t("incidentsBannerMessage", {
              priority: t("ticketPriorityP1"),
              duration: bannerOpenFor ?? "",
            }),
            p1Count > 1 ? t("kitIncidentBanner", { count: p1Count }) : null,
          ]
            .filter(Boolean)
            .join(" · ")}
          onPress={() => openIncident(banner.key)}
        />
      ) : null}
      <SegmentedControl
        values={INCIDENT_SEGMENTS.map((value) => t(SEGMENT_LABEL[value]))}
        selectedIndex={INCIDENT_SEGMENTS.indexOf(segment)}
        onChange={(index: number) => {
          const next: IncidentSegment | undefined = INCIDENT_SEGMENTS[index];
          if (next) setSegment(next);
        }}
      />
      <View className="flex-row">
        <PickerChip
          title={t("incidentsBoardPicker")}
          options={boardOptions}
          value={boardId ?? ALL_BOARDS}
          selected={!!boardId}
          onChange={(value) => setBoardId(value === ALL_BOARDS ? null : value)}
        />
      </View>
    </View>
  );

  let empty: React.ReactElement;
  if (list.isPending) {
    empty = (
      <View className="gap-3 px-4">
        <SkeletonCard />
        <SkeletonCard />
        <SkeletonCard />
      </View>
    );
  } else if (list.isError) {
    empty = (
      <EmptyState
        variant="error"
        title={t("incidentsError")}
        onAction={() => void list.refetch()}
        className="mt-12"
      />
    );
  } else {
    const emptyKey: TranslationKey = boardId
      ? "incidentsEmptyBoard"
      : segment === "open"
        ? "incidentsEmptyOpen"
        : segment === "mine"
          ? "incidentsEmptyMine"
          : "incidentsEmptyResolved";
    empty = (
      <EmptyState
        icon={segment === "resolved" ? CircleCheck : segment === "mine" ? UserRound : Siren}
        title={t(emptyKey)}
        className="mt-12"
      />
    );
  }

  const groupCountKey: TranslationKey = resolvedView
    ? "incidentsGroupResolved"
    : "incidentsGroupOpen";

  return (
    <SafeAreaView edges={["top"]} className="flex-1 bg-screen">
      <TabRootHeader
        title={t("tabIncidents")}
        trailing={
          Platform.OS === "ios" && canReport ? (
            <Button variant="destructive" size="sm" icon={Siren} onPress={report}>
              {t("incidentsReport")}
            </Button>
          ) : null
        }
      />
      <OfflineBanner />
      <SectionList<TicketListItem, Section>
        className="flex-1"
        contentInsetAdjustmentBehavior="automatic"
        contentContainerStyle={{
          paddingBottom: Platform.OS === "android" ? FAB_LIST_BOTTOM_PADDING + 40 : 40,
        }}
        sections={list.isError && !list.data ? [] : sections}
        extraData={tick}
        keyExtractor={(incident) => incident.id}
        ListHeaderComponent={header}
        ListEmptyComponent={empty}
        stickySectionHeadersEnabled={false}
        renderSectionHeader={({ section }) => (
          <View className="flex-row items-center gap-2 px-4 pb-2 pt-4">
            {section.priority === NO_PRIORITY || !resolvePriority(section.priority) ? (
              <Text variant="footnote" className="font-sans-medium text-foreground">
                {section.priority === NO_PRIORITY ? t("incidentsNoPriority") : section.priority}
              </Text>
            ) : (
              <PriorityIcon priority={section.priority} size={14} showLabel />
            )}
            <Text variant="meta" className="text-muted-foreground">
              {t(groupCountKey, { count: section.data.length })}
            </Text>
          </View>
        )}
        renderItem={({ item }) => (
          <IncidentRow
            incident={item}
            resolved={resolvedView}
            onPress={() => openIncident(item.key)}
          />
        )}
        ItemSeparatorComponent={() => <View className="h-2" />}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={refresh}
            tintColor={colors.mutedForeground}
          />
        }
      />
      {canReport ? (
        <Fab
          icon={Siren}
          label={t("incidentsReport")}
          tone="destructive"
          extended
          onPress={report}
        />
      ) : null}
    </SafeAreaView>
  );
}
