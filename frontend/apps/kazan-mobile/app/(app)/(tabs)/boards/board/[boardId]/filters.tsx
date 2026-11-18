import { useLocalSearchParams, useRouter } from "expo-router";
import { CalendarDays, Flag, Folder } from "lucide-react-native";
import * as React from "react";
import { View } from "react-native";

import {
  assigneeOptions,
  filterResultCount,
  type AssigneeOption,
} from "@/components/boards/boardForms";
import {
  activeFilterCount,
  EMPTY_FILTERS,
  parseBoardRoute,
  sprintForView,
  toggleStatus,
  toggleValue,
  type BoardFilters,
  type BoardRouteState,
  type TicketScope,
} from "@/components/boards/boardModel";
import { boardHref } from "@/components/boards/boardRoutes";
import { FilterTypeChip } from "@/components/boards/filters/FilterTypeChip";
import { FilterOptionChip } from "@/components/boards/filters/FilterOptionChip";
import { FilterSection } from "@/components/boards/filters/FilterSection";
import { FiltersFooter } from "@/components/boards/filters/FiltersFooter";
import { assigneeChoices, showsAssigneeSearch } from "@/components/boards/filters/filterSheetModel";
import { PickerRow } from "@/components/boards/PickerRow";
import { BottomSheet } from "@/components/kazan/BottomSheet";
import { InlineError } from "@/components/kazan/EmptyState";
import { ListSection } from "@/components/kazan/List";
import { SearchField } from "@/components/kazan/SearchField";
import { PriorityIcon } from "@/components/kazan/TicketMeta";
import {
  resolvePriority,
  resolveStatus,
  resolveTicketType,
  TICKET_STATUSES,
  TICKET_TYPES,
} from "@/components/kazan/ticketMetaModel";
import { LabelDot } from "@/components/ticket/LabelDot";
import { Avatar } from "@/components/ui/avatar";
import { Chip } from "@/components/ui/chip";
import { Text } from "@/components/ui/text";
import {
  useBacklog,
  useBoardLabels,
  useBoardMembers,
  useBoardMilestones,
  useBoardProjects,
  useBoardSprints,
  useBoardTickets,
} from "@/hooks/queries/boards";
import { useMe } from "@/hooks/queries/users";
import { useTranslation } from "@/i18n";
import type { BoardOption, Sprint } from "@/lib/schemas/boards";
import { cn } from "@/lib/utils";

/** The priorities a board ticket can have, highest first; incidents use P1 to P3 instead. */
const FILTER_PRIORITIES = ["HIGHEST", "HIGH", "MEDIUM", "LOW", "LOWEST"] as const;

/**
 * The Filters sheet of a board. The board's route state arrives in the params,
 * so the live count reads exactly the lists the view shows. The filters are a
 * draft until "Show N tickets" hands them back to the board; closing the
 * sheet any other way leaves the board as it was.
 */
export default function BoardFiltersSheet() {
  const { t } = useTranslation();
  const params = useLocalSearchParams<Record<string, string | string[]>>();
  const boardId: string | undefined = Array.isArray(params.boardId)
    ? params.boardId[0]
    : params.boardId;
  const state: BoardRouteState = parseBoardRoute(params);

  if (!boardId) return <BottomSheet title={t("boardFilters")}>{null}</BottomSheet>;
  return <FiltersSheet boardId={boardId} state={state} />;
}

type FiltersSheetProps = { boardId: string; state: BoardRouteState };

function FiltersSheet({ boardId, state }: FiltersSheetProps) {
  const { t } = useTranslation();
  const router = useRouter();
  const meId: string | undefined = useMe(true).data?.id;
  const [draft, setDraft] = React.useState<BoardFilters>(state.filters);
  const [assigneesExpanded, setAssigneesExpanded] = React.useState<boolean>(false);
  const [memberQuery, setMemberQuery] = React.useState<string>("");

  const members = useBoardMembers(boardId);
  const labels = useBoardLabels(boardId);
  const projects = useBoardProjects(boardId);
  const milestones = useBoardMilestones(boardId);

  // The same scopes as the board screen, so the count matches what the view will list.
  const sprints = useBoardSprints(boardId);
  const backlog = useBacklog(boardId);
  const sprint: Sprint | null = sprintForView(state.view, sprints.data ?? [], state.sprintId);
  const sprintScope: TicketScope | null = sprint ? { sprintId: sprint.id } : null;
  const backlogScope: TicketScope | null =
    backlog.data && (state.view === "all" || state.view === "backlog")
      ? { backlogId: backlog.data.id }
      : null;
  const sprintTickets = useBoardTickets(boardId, sprintScope, draft, state.search);
  const backlogTickets = useBoardTickets(boardId, backlogScope, draft, state.search);
  const count: number | null = filterResultCount([
    { enabled: sprintScope !== null, data: sprintTickets.data },
    { enabled: backlogScope !== null, data: backlogTickets.data },
  ]);

  const people: AssigneeOption[] = React.useMemo(
    () => assigneeOptions(members.data ?? [], meId),
    [members.data, meId]
  );
  const { shown, hidden } = assigneeChoices(people, draft.assignees, {
    expanded: assigneesExpanded,
    query: memberQuery,
  });
  const activeCount: number = activeFilterCount(draft);

  const apply = () => router.dismissTo(boardHref(boardId, { ...state, filters: draft }));
  const reset = () => setDraft(EMPTY_FILTERS);
  const optionsFailed: boolean =
    members.isError || labels.isError || projects.isError || milestones.isError;
  const retryOptions = () => {
    if (members.isError) void members.refetch();
    if (labels.isError) void labels.refetch();
    if (projects.isError) void projects.refetch();
    if (milestones.isError) void milestones.refetch();
  };

  return (
    <BottomSheet
      title={
        activeCount > 0 ? t("boardFiltersTitleCount", { count: activeCount }) : t("boardFilters")
      }
      headerAction={{ label: t("boardFiltersReset"), onPress: reset, disabled: activeCount === 0 }}
      footer={
        <FiltersFooter count={count} activeCount={activeCount} onApply={apply} onReset={reset} />
      }
    >
      {optionsFailed ? (
        <InlineError
          message={t("boardFilterOptionsError")}
          onRetry={retryOptions}
          className="mx-4 mt-4"
        />
      ) : null}

      <FilterSection title={t("boardFilterStatus")} selectedCount={draft.statuses.length}>
        {TICKET_STATUSES.map((status) => (
          <FilterOptionChip
            key={status}
            label={t(resolveStatus(status).labelKey)}
            leading={
              <View className={cn("size-2.5 rounded-full", resolveStatus(status).dotClassName)} />
            }
            selected={draft.statuses.includes(status)}
            onPress={() => setDraft({ ...draft, statuses: toggleStatus(draft.statuses, status) })}
          />
        ))}
      </FilterSection>

      <FilterSection
        title={t("boardFilterAssignee")}
        selectedCount={draft.assignees.length}
        accessory={
          showsAssigneeSearch(people.length) ? (
            <SearchField
              label={t("boardFilterSearchMembers")}
              value={memberQuery}
              onChangeText={setMemberQuery}
            />
          ) : null
        }
        emptyText={members.isSuccess && people.length === 0 ? t("boardFilterNoOptions") : null}
      >
        {shown.map((person) => (
          <FilterOptionChip
            key={person.id}
            label={person.isMe ? t("boardFilterMe") : person.name}
            accessibilityLabel={person.isMe ? `${t("boardFilterMe")}, ${person.name}` : person.name}
            leading={<Avatar name={person.name} size={20} />}
            selected={draft.assignees.includes(person.id)}
            onPress={() =>
              setDraft({ ...draft, assignees: toggleValue(draft.assignees, person.id) })
            }
          />
        ))}
        {hidden > 0 ? (
          <Chip
            size="md"
            label={t("boardFilterMore", { count: hidden })}
            onPress={() => setAssigneesExpanded(true)}
          />
        ) : null}
        {memberQuery.trim() && shown.length === 0 ? (
          <Text variant="subhead" className="text-muted-foreground">
            {t("boardFilterNoMembersMatch")}
          </Text>
        ) : null}
      </FilterSection>

      <FilterSection title={t("boardFilterType")} selectedCount={draft.types.length}>
        {TICKET_TYPES.map((type) => (
          <FilterTypeChip
            key={type}
            type={type}
            label={t(resolveTicketType(type).labelKey)}
            selected={draft.types.includes(type)}
            onPress={() => setDraft({ ...draft, types: toggleValue(draft.types, type) })}
          />
        ))}
      </FilterSection>

      <FilterSection title={t("boardFilterPriority")} selectedCount={draft.priorities.length}>
        {FILTER_PRIORITIES.map((priority) => (
          <FilterOptionChip
            key={priority}
            label={t(resolvePriority(priority)?.labelKey ?? "ticketPriorityMedium")}
            leading={<PriorityIcon priority={priority} />}
            selected={draft.priorities.includes(priority)}
            onPress={() =>
              setDraft({ ...draft, priorities: toggleValue(draft.priorities, priority) })
            }
          />
        ))}
      </FilterSection>

      <FilterSection
        title={t("boardFilterLabels")}
        selectedCount={draft.labels.length}
        emptyText={labels.isSuccess && labels.data.length === 0 ? t("boardFilterNoOptions") : null}
      >
        {(labels.data ?? []).map((label: BoardOption) => (
          <FilterOptionChip
            key={label.id}
            label={label.name}
            leading={<LabelDot colour={label.color} />}
            selected={draft.labels.includes(label.id)}
            onPress={() => setDraft({ ...draft, labels: toggleValue(draft.labels, label.id) })}
          />
        ))}
      </FilterSection>

      <ListSection>
        <PickerRow
          title={t("boardFilterProject")}
          icon={Folder}
          value={draft.project}
          options={(projects.data ?? []).map((project) => ({
            id: project.id,
            label: project.name,
          }))}
          noneLabel={t("boardFilterAny")}
          onChange={(project) => setDraft({ ...draft, project })}
        />
        <PickerRow
          title={t("boardFilterMilestone")}
          icon={Flag}
          iconTone="bg-warning"
          value={draft.milestone}
          options={(milestones.data ?? []).map((milestone) => ({
            id: milestone.id,
            label: milestone.name,
          }))}
          noneLabel={t("boardFilterAny")}
          onChange={(milestone) => setDraft({ ...draft, milestone })}
        />
        <PickerRow
          title={t("boardFilterDue")}
          icon={CalendarDays}
          iconTone="bg-destructive"
          value={draft.due}
          options={[{ id: "week", label: t("boardFilterThisWeek") }]}
          noneLabel={t("boardFilterAny")}
          onChange={(due) => setDraft({ ...draft, due: due === "week" ? "week" : null })}
        />
      </ListSection>
    </BottomSheet>
  );
}
