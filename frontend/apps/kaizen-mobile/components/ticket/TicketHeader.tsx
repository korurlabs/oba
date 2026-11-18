import { ChevronDown } from "lucide-react-native";
import * as React from "react";
import { Pressable, View, type TextInput } from "react-native";

import { StatusPill, TicketTypeBadge } from "@/components/kaizen/TicketMeta";
import { resolveTicketType } from "@/components/kaizen/ticketMetaModel";
import { PriorityChip, RankChip } from "@/components/ticket/TicketChips";
import type { TicketHeaderValue } from "@/components/ticket/ticketFieldsModel";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { TextField } from "@/components/ui/text-field";
import { useDates } from "@/hooks/useDates";
import { useTranslation } from "@/i18n";
import { TICKET_TITLE_MAX } from "@/lib/schemas/tickets";

/** The title field of a new ticket, in place of the title text. Always required. */
export interface TicketTitleInput {
  value: string;
  onChange: (text: string) => void;
  error: string | null;
  /** The text input, such as to focus it when Create finds the title missing. */
  inputRef?: React.Ref<TextInput>;
}

type TicketHeaderProps = {
  ticket: TicketHeaderValue;
  /** Opens the status picker; without it the status is read-only. */
  onChangeStatus?: () => void;
  /** Opens the priority picker; without it the priority is read-only. */
  onChangePriority?: () => void;
  /** Opens the mission rank sheet; without it the rank is read-only. */
  onOpenSizing?: () => void;
  /** Why the rank cannot be sent, such as missing or refused: a red chip and this message. */
  rankError?: string | null;
  /** Opens the type picker; without it the type is read-only. */
  onChangeType?: () => void;
  /** A new ticket's title field; a saved ticket shows its title as text. */
  titleInput?: TicketTitleInput;
  /** A warning under status and priority, such as what creating an incident does. */
  notice?: string;
};

/**
 * The top of a ticket: type, key and last change, the title, then status,
 * priority and mission rank side by side. Each control that has a handler
 * opens its picker; without one it is plain text, as for a guest. The rank is
 * required, as on the web: a missing one reads "Mission rank *", and one that
 * is missing at Create or refused by the API turns red with the message
 * under the chips. A new ticket has no key or last change yet, and edits its
 * title in place.
 */
export function TicketHeader({
  ticket,
  onChangeStatus,
  onChangePriority,
  onOpenSizing,
  rankError,
  onChangeType,
  titleInput,
  notice,
}: TicketHeaderProps) {
  const { t } = useTranslation();
  const dates = useDates();
  const updated: string | null = ticket.updatedAt ? dates.relativePast(ticket.updatedAt) : null;
  const typeLabel: string = t(resolveTicketType(ticket.type).labelKey);

  return (
    <View className="gap-3 px-4 pt-4">
      <View className="flex-row items-center gap-2">
        {onChangeType ? (
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={t("ticketTypeValue", { type: typeLabel })}
            onPress={onChangeType}
            hitSlop={12}
            className="flex-row items-center gap-1 active:opacity-70"
          >
            <TicketTypeBadge type={ticket.type} />
            <Icon as={ChevronDown} size={14} className="text-muted-foreground" />
          </Pressable>
        ) : (
          <TicketTypeBadge type={ticket.type} />
        )}
        {ticket.key ? (
          <Text variant="ticket-key" selectable>
            {ticket.key}
          </Text>
        ) : null}
        <View className="flex-1" />
        {updated ? (
          <Text variant="caption" className="text-muted-foreground" numberOfLines={1}>
            {t("ticketUpdated", { time: updated })}
          </Text>
        ) : null}
      </View>
      {titleInput ? (
        <TextField
          ref={titleInput.inputRef}
          label={t("newTicketTitleLabel")}
          required
          value={titleInput.value}
          onChangeText={titleInput.onChange}
          placeholder={t("newTicketTitlePlaceholder")}
          error={titleInput.error}
          maxLength={TICKET_TITLE_MAX}
          autoFocus
          returnKeyType="done"
        />
      ) : (
        <Text variant="title-2" selectable>
          {ticket.title}
        </Text>
      )}
      <View className="flex-row flex-wrap items-center gap-2">
        <StatusPill status={ticket.status} onPress={onChangeStatus} />
        <PriorityChip priority={ticket.priority} onPress={onChangePriority} />
        <RankChip rank={ticket.missionRank} onPress={onOpenSizing} required invalid={!!rankError} />
      </View>
      {rankError ? (
        <Text variant="footnote" className="-mt-1 text-destructive">
          {rankError}
        </Text>
      ) : null}
      {notice ? (
        <Text variant="footnote" className="text-destructive">
          {notice}
        </Text>
      ) : null}
    </View>
  );
}
