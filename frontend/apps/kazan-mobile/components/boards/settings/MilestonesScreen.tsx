import { Stack, useLocalSearchParams, useRouter } from "expo-router";
import { CircleCheck, Flag, Pencil, Plus, RotateCcw, Trash2 } from "lucide-react-native";
import * as React from "react";
import { RefreshControl, ScrollView, View } from "react-native";

import { milestoneFormHref } from "@/components/boards/boardRoutes";
import {
  milestoneDotColor,
  milestoneSections,
} from "@/components/boards/settings/boardSettingsModel";
import {
  useMilestoneActions,
  type MilestoneActionTarget,
} from "@/components/boards/settings/useMilestoneActions";
import { ActionSheet, type ActionSheetAction } from "@/components/kazan/ActionSheet";
import { EmptyState } from "@/components/kazan/EmptyState";
import { ListRow, ListSection } from "@/components/kazan/List";
import { IconButton } from "@/components/ui/icon-button";
import { SkeletonGroup, SkeletonRow } from "@/components/ui/skeleton";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useBoard, useBoardMilestones } from "@/hooks/queries/boards";
import { useDates } from "@/hooks/useDates";
import { useTranslation } from "@/i18n";
import type { DateFormatter } from "@/lib/dates";
import type { Milestone } from "@/lib/schemas/boards";

/** The milestone's action target: its own board when the list names it, else the listed board. */
function targetOf(milestone: Milestone, boardId: string): MilestoneActionTarget {
  return { boardId: milestone.boardId ?? boardId, milestoneId: milestone.id, name: milestone.name };
}

/**
 * The milestones of a board, in an Open and a Closed section, each by target
 * date, then name. Every reader may add, edit, close, reopen and delete, as
 * the web Roadmap offers them without a role check; the server refuses below
 * board admin and the app toasts its message. A tap opens the Milestone
 * sheet; a long press offers the same actions as a menu.
 */
export function MilestonesScreen() {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const dates: DateFormatter = useDates();
  const router = useRouter();
  const params = useLocalSearchParams<{ boardId?: string | string[] }>();
  const boardId: string =
    (Array.isArray(params.boardId) ? params.boardId[0] : params.boardId) ?? "";
  const list = useBoardMilestones(boardId || undefined);
  const boardColor: string | null | undefined = useBoard(boardId || undefined).data?.color;
  const { close, reopen, confirmDelete } = useMilestoneActions();
  const [menuMilestone, setMenuMilestone] = React.useState<Milestone | null>(null);
  const [refreshing, setRefreshing] = React.useState<boolean>(false);

  const sections = React.useMemo(() => milestoneSections(list.data ?? []), [list.data]);

  const openAdd = React.useCallback(() => {
    router.push(milestoneFormHref(boardId));
  }, [router, boardId]);

  const openEdit = React.useCallback(
    (milestone: Milestone) => {
      router.push(milestoneFormHref(boardId, milestone.id));
    },
    [router, boardId]
  );

  const refresh = React.useCallback(() => {
    setRefreshing(true);
    list
      .refetch()
      .catch((error: unknown) => {
        // A failed read shows in the query state and the app's error layer; this only records the pull.
        console.warn("[milestones] refresh failed", error instanceof Error ? error.message : error);
      })
      .finally(() => setRefreshing(false));
  }, [list]);

  /** "Nov 16, 2026 – Dec 18, 2026", or "Target Dec 18, 2026" without a start. */
  const dateLine = (milestone: Milestone): string | undefined => {
    const target: string | null = milestone.targetDate ? dates.date(milestone.targetDate) : null;
    if (!target) return undefined;
    const start: string | null = milestone.startDate ? dates.date(milestone.startDate) : null;
    return start
      ? t("boardMilestonesDateRange", { start, target })
      : t("boardMilestonesTargetOnly", { target });
  };

  const menuActions: ActionSheetAction[] = menuMilestone
    ? [
        {
          label: t("boardOptionsEdit"),
          icon: Pencil,
          onPress: () => openEdit(menuMilestone),
        },
        menuMilestone.status === "CLOSED"
          ? {
              label: t("boardMilestonesReopen"),
              icon: RotateCcw,
              onPress: () => reopen(targetOf(menuMilestone, boardId)),
            }
          : {
              label: t("boardMilestonesClose"),
              icon: CircleCheck,
              onPress: () => close(targetOf(menuMilestone, boardId)),
            },
        {
          label: t("boardOptionsDelete"),
          icon: Trash2,
          destructive: true,
          onPress: () => confirmDelete(targetOf(menuMilestone, boardId)),
        },
      ]
    : [];

  const header = (
    <Stack.Screen
      options={{
        headerRight: () => (
          <IconButton icon={Plus} label={t("boardMilestonesAdd")} onPress={openAdd} />
        ),
      }}
    />
  );

  if (list.isPending) {
    return (
      <View className="flex-1 bg-screen pt-6">
        {header}
        <SkeletonGroup>
          <SkeletonRow />
          <SkeletonRow />
          <SkeletonRow />
        </SkeletonGroup>
      </View>
    );
  }

  if (list.isError && !list.data) {
    return (
      <View className="flex-1 bg-screen">
        {header}
        <EmptyState
          variant="error"
          title={t("boardMilestonesError")}
          onAction={() => void list.refetch()}
          className="mt-16"
        />
      </View>
    );
  }

  const renderRow = (milestone: Milestone) => (
    <ListRow
      key={milestone.id}
      title={milestone.name}
      subtitle={dateLine(milestone)}
      value={t("boardMilestonesProgress", {
        done: milestone.doneCount ?? 0,
        total: milestone.totalCount ?? 0,
      })}
      dotColor={milestoneDotColor(milestone, boardColor) ?? undefined}
      onPress={() => openEdit(milestone)}
      onLongPress={() => setMenuMilestone(milestone)}
    />
  );

  return (
    <>
      {header}
      <ScrollView
        className="flex-1 bg-screen"
        contentInsetAdjustmentBehavior="automatic"
        contentContainerClassName="flex-grow pb-10"
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={refresh}
            tintColor={colors.mutedForeground}
          />
        }
      >
        {sections.open.length === 0 && sections.closed.length === 0 ? (
          <EmptyState
            icon={Flag}
            title={t("boardMilestonesEmptyTitle")}
            message={t("boardMilestonesEmptyBody")}
            actionLabel={t("boardMilestonesAdd")}
            onAction={openAdd}
            className="mt-16"
          />
        ) : (
          <>
            {sections.open.length > 0 ? (
              <ListSection header={t("boardMilestonesOpenSection")}>
                {sections.open.map(renderRow)}
              </ListSection>
            ) : null}
            {sections.closed.length > 0 ? (
              <ListSection header={t("boardMilestonesClosedSection")}>
                {sections.closed.map(renderRow)}
              </ListSection>
            ) : null}
          </>
        )}
      </ScrollView>
      <ActionSheet
        visible={menuMilestone !== null}
        onClose={() => setMenuMilestone(null)}
        title={menuMilestone?.name}
        actions={menuActions}
      />
    </>
  );
}
