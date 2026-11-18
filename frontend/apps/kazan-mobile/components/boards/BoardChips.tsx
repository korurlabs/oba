import { SlidersHorizontal, User } from "lucide-react-native";
import * as React from "react";
import { ScrollView } from "react-native";

import { activeFilterCount, type BoardRouteState } from "@/components/boards/boardModel";
import { hapticSelection } from "@/components/boards/dragFeedback";
import { Chip } from "@/components/ui/chip";
import { useTranslation } from "@/i18n";

type BoardChipsProps = {
  state: BoardRouteState;
  meId: string | undefined;
  onChange: (state: BoardRouteState) => void;
  /**
   * Opens the Filters sheet. Without it the chip is left out, since a filter
   * the user cannot see or change would hide tickets with no explanation.
   */
  onOpenFilters?: () => void;
  /**
   * Adds Status and Assignee shortcuts before Type, for the All view: it lists
   * every status in one place and the whole team's tickets, so where a ticket
   * stands and whose it is are the first questions.
   */
  showStatusAndAssignee?: boolean;
  /** Chips before Filters, such as the sprint picker. */
  children?: React.ReactNode;
};

/**
 * The chip row above a board view: any view chips, then "Filters" with the
 * number of filter groups set, "Only mine", and the Status and Assignee (when
 * asked for), Type and Labels shortcuts, which open the same Filters sheet and
 * count their own picks.
 */
function BoardChips({
  state,
  meId,
  onChange,
  onOpenFilters,
  showStatusAndAssignee = false,
  children,
}: BoardChipsProps) {
  const { t } = useTranslation();
  const mine: boolean =
    !!meId && state.filters.assignees.length === 1 && state.filters.assignees[0] === meId;
  const count: number = activeFilterCount(state.filters);

  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      contentContainerClassName="gap-2 px-4 py-2"
      className="grow-0"
    >
      {children}
      {onOpenFilters ? (
        <Chip
          label={t("boardFilters")}
          icon={SlidersHorizontal}
          count={count > 0 ? count : null}
          selected={count > 0}
          onPress={onOpenFilters}
        />
      ) : null}
      {meId ? (
        <Chip
          label={t("boardOnlyMine")}
          icon={User}
          selected={mine}
          onPress={() => {
            hapticSelection();
            onChange({
              ...state,
              filters: { ...state.filters, assignees: mine ? [] : [meId] },
            });
          }}
        />
      ) : null}
      {onOpenFilters ? (
        <>
          {showStatusAndAssignee ? (
            <>
              <Chip
                label={t("boardFilterStatus")}
                dropdown
                count={state.filters.statuses.length > 0 ? state.filters.statuses.length : null}
                selected={state.filters.statuses.length > 0}
                onPress={onOpenFilters}
              />
              <Chip
                label={t("boardFilterAssignee")}
                dropdown
                count={state.filters.assignees.length > 0 ? state.filters.assignees.length : null}
                selected={state.filters.assignees.length > 0}
                onPress={onOpenFilters}
              />
            </>
          ) : null}
          <Chip
            label={t("boardFilterType")}
            dropdown
            count={state.filters.types.length > 0 ? state.filters.types.length : null}
            selected={state.filters.types.length > 0}
            onPress={onOpenFilters}
          />
          <Chip
            label={t("boardFilterLabels")}
            dropdown
            count={state.filters.labels.length > 0 ? state.filters.labels.length : null}
            selected={state.filters.labels.length > 0}
            onPress={onOpenFilters}
          />
        </>
      ) : null}
    </ScrollView>
  );
}

export { BoardChips };
export type { BoardChipsProps };
