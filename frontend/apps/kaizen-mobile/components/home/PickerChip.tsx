import * as React from "react";

import { ActionSheet, type ActionSheetAction } from "@/components/kaizen/ActionSheet";
import { Chip } from "@/components/ui/chip";
import { hapticSelection } from "@/lib/haptics";

type PickerOption<T extends string> = {
  value: T;
  label: string;
};

type PickerChipProps<T extends string> = {
  /** The title of the choice sheet, such as "Period" or "Board". */
  title: string;
  options: readonly PickerOption<T>[];
  value: T;
  onChange: (value: T) => void;
  /** Draws the chip as selected, such as a board filter other than "All boards". */
  selected?: boolean;
  className?: string;
};

/**
 * A chip that shows the current choice and opens a sheet of the options: the
 * pull-down select of the dashboard and the board filter of My Tickets.
 */
function PickerChip<T extends string>({
  title,
  options,
  value,
  onChange,
  selected = false,
  className,
}: PickerChipProps<T>) {
  const [open, setOpen] = React.useState<boolean>(false);
  const current: PickerOption<T> | undefined = options.find((option) => option.value === value);

  const actions: ActionSheetAction[] = options.map((option) => ({
    label: option.label,
    selected: option.value === value,
    onPress: () => {
      if (option.value === value) return;
      hapticSelection();
      onChange(option.value);
    },
  }));

  return (
    <>
      <Chip
        label={current?.label ?? title}
        accessibilityLabel={`${title}: ${current?.label ?? ""}`}
        dropdown
        selected={selected}
        onPress={() => setOpen(true)}
        className={className}
      />
      <ActionSheet visible={open} onClose={() => setOpen(false)} title={title} actions={actions} />
    </>
  );
}

export { PickerChip };
export type { PickerChipProps, PickerOption };
