import { useQueryClient } from "@tanstack/react-query";
import { useRouter } from "expo-router";
import { Kanban, Link2, ListTodo } from "lucide-react-native";
import * as React from "react";
import { RefreshControl, ScrollView } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { canCreateBoard } from "@/components/boards/boardModel";
import { useBoardLinks } from "@/components/boards/boardNavigation";
import { DeadlinesSection } from "@/components/home/DeadlinesSection";
import { LeaderboardSection } from "@/components/home/LeaderboardSection";
import { SprintSections } from "@/components/home/SprintSections";
import { StaleBacklogSection } from "@/components/home/StaleBacklogSection";
import { canWriteTickets } from "@/components/home/homeModel";
import { OpenP1IncidentBanner } from "@/components/incidents/OpenP1IncidentBanner";
import { EmptyState, InlineError } from "@/components/kazan/EmptyState";
import { ListRow, ListSection } from "@/components/kazan/List";
import { OfflineBanner } from "@/components/kazan/OfflineBanner";
import { TAB_ROOT_CONTENT_TOP, TabRootHeader } from "@/components/kazan/TabRootHeader";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { boardKeys, boardTicketKeys, useBoards } from "@/hooks/queries/boards";
import { homeKeys, useHomeSprint } from "@/hooks/queries/home";
import { useMe } from "@/hooks/queries/users";
import { useOrganizationRole } from "@/hooks/useOrganizationRole";
import { useTranslation } from "@/i18n";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import { HomeLeaderboardPeriod } from "@/lib/schemas/home";
import { cn } from "@/lib/utils";

/**
 * The Home dashboard: the open P1 incident banner, the active sprint and its
 * burndown, the leaderboard, upcoming deadlines and the stale backlog. Each
 * section loads, fails and retries on its own, so one slow or broken read
 * never blanks the screen.
 * An organization without boards gets the "No boards yet" state instead.
 */
export default function HomeScreen() {
  const { t } = useTranslation();
  const router = useRouter();
  const colors = useThemeColors();
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  const { openNewBoard } = useBoardLinks();
  const role: string | null = useOrganizationRole();
  const meId: string | undefined = useMe(true).data?.id;
  const boards = useBoards();

  // No choice lets the server pick the focus board; a choice scopes sprint and
  // burndown, and only in the organization it was made in.
  const [boardChoice, setBoardChoice] = React.useState<{
    orgId: string | null;
    boardId: string;
  } | null>(null);
  const boardId: string | null = boardChoice?.orgId === orgId ? boardChoice.boardId : null;
  const [period, setPeriod] = React.useState<HomeLeaderboardPeriod>(HomeLeaderboardPeriod.SPRINT);
  const [refreshing, setRefreshing] = React.useState<boolean>(false);
  const sprint = useHomeSprint(boardId);

  const openLinks = React.useCallback(() => router.push("/home/links"), [router]);

  const refresh = React.useCallback(() => {
    if (!orgId) return;
    setRefreshing(true);
    Promise.all([
      queryClient.refetchQueries({ queryKey: homeKeys.all(orgId), type: "active" }),
      queryClient.refetchQueries({ queryKey: boardKeys.all(orgId), type: "active" }),
      queryClient.refetchQueries({ queryKey: boardTicketKeys.openP1(orgId), type: "active" }),
    ])
      .catch((error: unknown) => {
        // Each section shows its own failure; this only records the pull.
        console.warn("[home] refresh failed", error instanceof Error ? error.message : error);
      })
      .finally(() => setRefreshing(false));
  }, [orgId, queryClient]);

  const noBoards: boolean = boards.isSuccess && boards.data.total === 0;
  return (
    <SafeAreaView edges={["top"]} className="flex-1 bg-screen">
      <TabRootHeader
        title={t("tabHome")}
        actions={[{ icon: Link2, label: t("homeLinks"), onPress: openLinks }]}
      />
      <OfflineBanner />
      <ScrollView
        contentInsetAdjustmentBehavior="automatic"
        contentContainerClassName={cn("gap-6 pb-10", TAB_ROOT_CONTENT_TOP)}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={refresh}
            tintColor={colors.mutedForeground}
          />
        }
      >
        <OpenP1IncidentBanner className="mx-4" />
        {boards.isError ? (
          <InlineError
            message={t("homeBoardsError")}
            onRetry={() => void boards.refetch()}
            className="mx-4"
          />
        ) : null}
        {noBoards ? (
          <EmptyState
            icon={Kanban}
            title={t("homeNoBoardsTitle")}
            message={t("homeNoBoardsBody")}
            actionLabel={canCreateBoard(role) ? t("homeNoBoardsAction") : undefined}
            onAction={canCreateBoard(role) ? openNewBoard : undefined}
            className="mt-16"
          />
        ) : (
          <>
            <ListSection className="mt-0">
              <ListRow
                title={t("homeMyTickets")}
                subtitle={t("homeMyTicketsHint")}
                icon={ListTodo}
                onPress={() => router.push("/home/my-tickets")}
              />
            </ListSection>
            <SprintSections
              query={sprint}
              fallbackBoardSlug={boards.data?.boards[0]?.slug ?? null}
              onChooseBoard={(id) => setBoardChoice({ orgId, boardId: id })}
            />
            <LeaderboardSection period={period} onPeriodChange={setPeriod} meId={meId} />
            <DeadlinesSection />
            <StaleBacklogSection canWrite={canWriteTickets(role)} />
          </>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}
