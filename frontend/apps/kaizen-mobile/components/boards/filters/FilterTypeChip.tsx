import * as React from "react";
import { Platform, Pressable } from "react-native";

import { TicketTypeBadge } from "@/components/kaizen/TicketMeta";
import { hapticSelection } from "@/lib/haptics";
import { cn } from "@/lib/utils";

/** Grows the ringed tag to the platform's touch minimum: 44 pt on iOS, 48 dp on Android. */
const RING_HIT_SLOP = Platform.select({
  android: { top: 6, bottom: 6, left: 2, right: 2 },
  default: { top: 4, bottom: 4, left: 2, right: 2 },
});

type FilterTypeChipProps = {
  type: string;
  /** The type's spoken name, since the tag alone carries it visually. */
  label: string;
  selected: boolean;
  onPress: () => void;
};

/**
 * One option of the type filter, drawn as the coloured tag the ticket's own
 * type picker offers, so a type looks alike wherever it is chosen. The tag is
 * padded to 28 tall, so with its ring it lines up with the 36 tall chips of
 * the other sections. Selected is a `primary` ring with a small gap around the
 * tag, which reads on every type colour in both themes; unselected keeps a
 * clear ring of the same width, so choosing a type never shifts the row.
 */
function FilterTypeChip({ type, label, selected, onPress }: FilterTypeChipProps) {
  return (
    <Pressable
      accessibilityRole="checkbox"
      accessibilityLabel={label}
      accessibilityState={{ checked: selected }}
      hitSlop={RING_HIT_SLOP}
      onPress={() => {
        hapticSelection();
        onPress();
      }}
      className={cn(
        "max-w-full rounded-lg border-2 p-0.5",
        selected ? "border-primary" : "border-transparent",
        Platform.select({ ios: "active:opacity-60", android: "active:opacity-70" })
      )}
    >
      <TicketTypeBadge type={type} className="min-h-7 rounded-sm px-2.5" />
    </Pressable>
  );
}

export { FilterTypeChip };
export type { FilterTypeChipProps };
