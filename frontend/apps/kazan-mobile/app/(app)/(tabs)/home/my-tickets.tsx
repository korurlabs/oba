import { Stack, useRouter } from "expo-router";
import { Inbox, Plus, Search, Siren, CalendarDays } from "lucide-react-native";
import * as React from "react";
import { RefreshControl, ScrollView, SectionList, View } from "react-native";

import { useHomeLinks } from "@/components/home/homeNavigation";
import {
  canWriteTickets,
  groupTicketsByStatus,
  type TicketGroup,
} from "@/components/home/homeModel";
import { MyTicketRow } from "@/components/home/MyTicketRow";
import { PickerChip, type PickerOption } from "@/components/home/PickerChip";
import { EmptyState } from "@/components/kazan/EmptyState";
import { StatusPill } from "@/components/kazan/TicketMeta";
import { newTicketHref } from "@/components/ticket/ticketRoutes";
import { Chip } from "@/components/ui/chip";
import { IconButton } from "@/components/ui/icon-button";
import { SegmentedControl } from "@/components/ui/segmented-control";
import { SkeletonCard } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useBoards } from "@/hooks/queries/boards";
import type { MyTicketsFilters, MyTicketsMode } from "@/hooks/queries/ticketParams";
import { useMyTickets } from "@/hooks/queries/tickets";
import { useDebouncedSearch } from "@/hooks/useDebouncedSearch";
import { useOrganizationRole } from "@/hooks/useOrganizationRole";
import { useTranslation } from "@/i18n";
import { hapticSelection } from "@/lib/haptics";
import type { TicketListItem } from "@/lib/schemas/tickets";

const MODES: readonly MyTicketsMode[] = ["assigned", "reported"];
/** The board filter's value for every board; a board id is never this string. */
const ALL_BOARDS = "all";

type Section = { status: string; data: TicketListItem[] };

/**
 * My open tickets across every board: assigned to me or reported by me,
 * grouped by status, with filters for one board, this week's due dates and
 * incidents, and a search on title or key in the nav bar. New ticket starts
 * on the board the board filter picks, else on the board opened last.
 */
export default function MyTicketsScreen() {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const { openTicket } = useHomeLinks();
  const [mode, setMode] = React.useState<MyTicketsMode>("assigned");
  const [filters, setFilters] = React.useState<MyTicketsFilters>({
    boardId: null,
    dueThisWeek: false,
    incidentsOnly: false,
  });
  const [refreshing, setRefreshing] = React.useState<boolean>(false);
  const [search, setSearch] = React.useState<string>("");
  const [, setSearchText] = useDebouncedSearch(search, setSearch);
  const router = useRouter();
  const tickets = useMyTickets(mode, filters, search);
  const boards = useBoards();
  const canAddTicket: boolean = canWriteTickets(useOrganizationRole());

  const boardOptions: PickerOption<string>[] = [
    { value: ALL_BOARDS, label: t("myTicketsAllBoards") },
    ...(boards.data?.boards ?? []).map((board) => ({ value: board.id, label: board.name })),
  ];
  const filtered: boolean = !!filters.boardId || filters.dueThisWeek || filters.incidentsOnly;

  const refresh = React.useCallback(() => {
    setRefreshing(true);
    tickets
      .refetch()
      .catch((error: unknown) => {
        // The list shows the failure; this only records the pull.
        console.warn("[my-tickets] refresh failed", error instanceof Error ? error.message : error);
      })
      .finally(() => setRefreshing(false));
  }, [tickets]);

  const sections: Section[] = React.useMemo(
    () =>
      groupTicketsByStatus(tickets.data ?? []).map((group: TicketGroup) => ({
        status: group.status,
        data: group.tickets,
      })),
    [tickets.data]
  );

  const header = (
    <View className="gap-3 px-4 pb-3 pt-2">
      <SegmentedControl
        values={MODES.map((value) =>
          t(value === "assigned" ? "myTicketsAssigned" : "myTicketsReported")
        )}
        selectedIndex={MODES.indexOf(mode)}
        onChange={(index: number) => {
          const next: MyTicketsMode | undefined = MODES[index];
          if (next) setMode(next);
        }}
      />
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerClassName="gap-2"
      >
        <PickerChip
          title={t("myTicketsBoardPicker")}
          options={boardOptions}
          value={filters.boardId ?? ALL_BOARDS}
          selected={!!filters.boardId}
          onChange={(value) =>
            setFilters((current) => ({ ...current, boardId: value === ALL_BOARDS ? null : value }))
          }
        />
        <Chip
          label={t("myTicketsDueThisWeek")}
          icon={CalendarDays}
          selected={filters.dueThisWeek}
          onPress={() => {
            hapticSelection();
            setFilters((current) => ({ ...current, dueThisWeek: !current.dueThisWeek }));
          }}
        />
        <Chip
          label={t("myTicketsIncidents")}
          icon={Siren}
          selected={filters.incidentsOnly}
          onPress={() => {
            hapticSelection();
            setFilters((current) => ({ ...current, incidentsOnly: !current.incidentsOnly }));
          }}
        />
      </ScrollView>
    </View>
  );

  const screenOptions = (
    <Stack.Screen
      options={{
        headerRight: canAddTicket
          ? () => (
              <IconButton
                icon={Plus}
                label={t("boardNewTicket")}
                onPress={() => router.push(newTicketHref({ boardId: filters.boardId }))}
              />
            )
          : undefined,
        headerSearchBarOptions: {
          placeholder: t("myTicketsSearchPlaceholder"),
          hideWhenScrolling: false,
          autoCapitalize: "none",
          onChangeText: (event) => setSearchText(event.nativeEvent.text),
          onCancelButtonPress: () => setSearchText(""),
          onClose: () => setSearchText(""),
        },
      }}
    />
  );

  let empty: React.ReactElement;
  if (tickets.isPending) {
    empty = (
      <View className="gap-3 px-4">
        <SkeletonCard />
        <SkeletonCard />
        <SkeletonCard />
      </View>
    );
  } else if (tickets.isError) {
    empty = (
      <EmptyState
        variant="error"
        title={t("myTicketsError")}
        onAction={() => void tickets.refetch()}
        className="mt-12"
      />
    );
  } else {
    empty = (
      <EmptyState
        icon={search ? Search : Inbox}
        title={
          search
            ? t("myTicketsSearchEmpty", { query: search })
            : filtered
              ? t("myTicketsEmptyFiltered")
              : t(mode === "assigned" ? "myTicketsEmptyAssigned" : "myTicketsEmptyReported")
        }
        className="mt-12"
      />
    );
  }

  return (
    <>
      {screenOptions}
      <SectionList<TicketListItem, Section>
        className="flex-1 bg-screen"
        contentInsetAdjustmentBehavior="automatic"
        contentContainerClassName="pb-10"
        sections={tickets.isError && !tickets.data ? [] : sections}
        keyExtractor={(ticket) => ticket.id}
        ListHeaderComponent={header}
        ListEmptyComponent={empty}
        stickySectionHeadersEnabled={false}
        renderSectionHeader={({ section }) => (
          <View className="flex-row items-center gap-2 px-4 pb-2 pt-4">
            <StatusPill status={section.status} />
            <Text variant="footnote" className="text-muted-foreground">
              {section.data.length}
            </Text>
          </View>
        )}
        renderItem={({ item }) => (
          <MyTicketRow ticket={item} onPress={() => openTicket(item.key)} />
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
    </>
  );
}
