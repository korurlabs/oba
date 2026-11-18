import * as React from "react";
import { ActivityIndicator, View } from "react-native";

import { BottomSheet } from "@/components/kaizen/BottomSheet";
import { InlineError } from "@/components/kaizen/EmptyState";
import { ListRow, ListSection } from "@/components/kaizen/List";
import { DismissWhen } from "@/components/ticket/DismissWhen";
import {
  assigneeCandidates,
  assigneeIdsOf,
  assigneesFor,
  sameIds,
  type AssigneeCandidate,
} from "@/components/ticket/ticketActionsModel";
import { personName } from "@/components/ticket/ticketModel";
import { useDraftTicketField, useSavedTicketField } from "@/components/ticket/useFieldSheet";
import { Input } from "@/components/ui/input";
import { Text } from "@/components/ui/text";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useBoardMembers } from "@/hooks/queries/boards";
import { useMe } from "@/hooks/queries/users";
import { useTranslation, type TranslationKey } from "@/i18n";
import type { TicketPerson } from "@/lib/schemas/tickets";

/** The label of a board role in the picker rows. */
const BOARD_ROLE_LABEL: Record<string, TranslationKey> = {
  OWNER: "ticketBoardRoleOwner",
  ADMIN: "ticketBoardRoleAdmin",
  MEMBER: "ticketBoardRoleMember",
  VIEWER: "ticketBoardRoleViewer",
};

type AssigneePickerSheetProps = {
  /** The board whose members are listed. */
  boardId: string | null | undefined;
  /** The assigned user ids; undefined while the ticket loads. */
  value: readonly string[] | undefined;
  /** The people assigned now, so one who left the board can still be removed. */
  assigned?: readonly TicketPerson[];
  /** Receives the full new list on Done, only when it differs from `value`. */
  onChange: (userIds: string[], candidates: readonly AssigneeCandidate[]) => void;
  /** A spinner on Done while the change is saved. */
  saving?: boolean;
  /** Closes the sheet once true, such as when the change has landed. */
  closed: boolean;
  /** Why the ticket could not be read, with its retry. */
  loadError?: unknown;
  onRetryLoad?: () => void;
};

/**
 * The assignee picker of a ticket: the board's members, searchable, any
 * number picked, handed over on Done as the full list. Done without a change
 * only closes it.
 */
export function AssigneePickerSheet({
  boardId,
  value,
  assigned = [],
  onChange,
  saving = false,
  closed,
  loadError,
  onRetryLoad,
}: AssigneePickerSheetProps) {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const meId: string | undefined = useMe(true).data?.id;
  const members = useBoardMembers(boardId ?? undefined);
  const [query, setQuery] = React.useState<string>("");
  const [picked, setPicked] = React.useState<string[] | null>(null);
  const [unchangedDone, setUnchangedDone] = React.useState<boolean>(false);

  const unknown: string = t("ticketUnknownPerson");
  const currentIds: readonly string[] = value ?? [];
  const selected: string[] = picked ?? [...currentIds];
  const changed: boolean = picked !== null && !sameIds(picked, currentIds);
  const everyone: AssigneeCandidate[] = assigneeCandidates(
    members.data ?? [],
    assigned,
    meId,
    "",
    unknown
  );
  const rows: AssigneeCandidate[] = assigneeCandidates(
    members.data ?? [],
    assigned,
    meId,
    query,
    unknown
  );

  const toggle = (userId: string) =>
    setPicked(
      selected.includes(userId) ? selected.filter((id) => id !== userId) : [...selected, userId]
    );

  const done = () => {
    if (changed) onChange(selected, everyone);
    else setUnchangedDone(true);
  };

  const subtitleOf = (candidate: AssigneeCandidate, blocked: boolean): string | undefined => {
    if (blocked) return t("memberNoKaizenAccess");
    if (candidate.userId === meId) return t("ticketAssigneeYou");
    if (!candidate.role) return t("ticketAssigneeLeftBoard");
    const key: TranslationKey | undefined = BOARD_ROLE_LABEL[candidate.role];
    return key ? t(key) : undefined;
  };

  const failure: unknown = loadError ?? members.error;

  return (
    <BottomSheet
      title={t("ticketFieldAssignees")}
      confirmLabel={t("ticketDone")}
      onConfirm={done}
      confirmDisabled={!value}
      confirmLoading={saving}
      detent="medium"
      dirty={changed && !closed}
      scroll
    >
      <DismissWhen when={closed || unchangedDone} />
      <View className="px-4 pt-2">
        <Input
          value={query}
          onChangeText={setQuery}
          placeholder={t("ticketAssigneeSearch")}
          accessibilityLabel={t("ticketAssigneeSearch")}
          autoCapitalize="none"
          autoCorrect={false}
          returnKeyType="search"
        />
      </View>
      {failure ? (
        <InlineError
          message={t("ticketAssigneesError")}
          onRetry={() => {
            if (loadError) onRetryLoad?.();
            else void members.refetch();
          }}
          className="m-4"
        />
      ) : !value || members.isPending ? (
        <View className="items-center py-10">
          <ActivityIndicator color={colors.mutedForeground} />
        </View>
      ) : rows.length === 0 ? (
        <Text className="px-6 py-8 text-center font-sans text-subhead text-muted-foreground">
          {t("ticketAssigneeSearchEmpty", { query: query.trim() })}
        </Text>
      ) : (
        <ListSection footer={t("ticketAssigneeFooter")}>
          {rows.map((candidate) => {
            const name: string = personName(candidate, unknown);
            const checked: boolean = selected.includes(candidate.userId);
            // The API refuses a new assignee without Kaizen; one already assigned stays removable.
            const blocked: boolean = !candidate.kaizenAccess && !checked;
            return (
              <ListRow
                key={candidate.userId}
                title={name}
                subtitle={subtitleOf(candidate, blocked)}
                avatar={{ name }}
                accessory="check"
                checked={checked}
                disabled={blocked}
                onPress={() => toggle(candidate.userId)}
              />
            );
          })}
        </ListSection>
      )}
    </BottomSheet>
  );
}

/** The assignee picker over a saved ticket: Done saves the list at once. */
export function TicketAssigneesSheet() {
  const { ticketRef, ticket, error, retry, saving, saved, save } = useSavedTicketField();
  return (
    <AssigneePickerSheet
      boardId={ticketRef?.boardId}
      value={ticket ? assigneeIdsOf(ticket) : undefined}
      assigned={ticket?.assignees ?? []}
      onChange={(assigneeIds, candidates) =>
        save({
          body: { assigneeIds },
          optimistic: (detail) => ({
            ...detail,
            assignees: assigneesFor(assigneeIds, candidates, detail.assignees ?? []),
          }),
        })
      }
      saving={saving}
      closed={saved}
      loadError={error}
      onRetryLoad={() => void retry()}
    />
  );
}

/** The assignee picker over the New ticket draft: Done keeps the list for Create. */
export function DraftAssigneesSheet() {
  const { fields, save, closed } = useDraftTicketField();
  return (
    <AssigneePickerSheet
      boardId={fields?.boardId}
      value={fields?.assigneeIds}
      onChange={(assigneeIds) => save({ assigneeIds })}
      closed={closed}
    />
  );
}
