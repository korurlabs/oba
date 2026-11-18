import * as React from "react";
import { Pressable, View } from "react-native";

import { BoardTicketCard } from "@/components/boards/BoardTicketCard";
import type { SelectionGroup } from "@/components/boards/selection/useTicketSelection";
import { resolveStatus } from "@/components/kaizen/ticketMetaModel";
import { CheckboxMark } from "@/components/ui/checkbox";
import { useTranslation } from "@/i18n";
import type { BoardTicket } from "@/lib/schemas/boards";
import { cn } from "@/lib/utils";

type SelectableTicketRowProps = {
  ticket: BoardTicket;
  boardId: string;
  group: SelectionGroup;
  selected: boolean;
  /** A row of the group that does not hold the selection. */
  disabled: boolean;
  onToggle: (group: SelectionGroup, id: string) => void;
  showStatus?: boolean;
  showPlanned?: boolean;
};

/**
 * A ticket row in selection mode: a leading checkbox and the card, one touch
 * target that picks or unpicks the ticket. Swipes, drags and the hold menu
 * are off while selecting, so the row does nothing else.
 */
const SelectableTicketRow = React.memo(function SelectableTicketRow({
  ticket,
  boardId,
  group,
  selected,
  disabled,
  onToggle,
  showStatus = true,
  showPlanned = false,
}: SelectableTicketRowProps) {
  const { t } = useTranslation();
  const toggle = React.useCallback(() => onToggle(group, ticket.id), [group, onToggle, ticket.id]);
  return (
    <Pressable
      accessible
      accessibilityRole="checkbox"
      accessibilityState={{ checked: selected, disabled }}
      accessibilityLabel={`${t("boardSelectRow", { key: ticket.key, title: ticket.title })}, ${t(
        resolveStatus(ticket.status).labelKey
      )}`}
      disabled={disabled}
      onPress={toggle}
      className={cn("mx-4 flex-row items-center gap-3 active:opacity-70", disabled && "opacity-50")}
    >
      <CheckboxMark checked={selected} disabled={disabled} />
      <View
        pointerEvents="none"
        importantForAccessibility="no-hide-descendants"
        accessibilityElementsHidden
        className="flex-1"
      >
        <BoardTicketCard
          ticket={ticket}
          boardId={boardId}
          showStatus={showStatus}
          showPlanned={showPlanned}
          onPress={toggle}
        />
      </View>
    </Pressable>
  );
});

export { SelectableTicketRow };
