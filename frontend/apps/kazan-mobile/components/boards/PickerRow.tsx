import type { LucideIcon } from "lucide-react-native";
import * as React from "react";

import { ActionSheet, type ActionSheetAction } from "@/components/kazan/ActionSheet";
import { ListRow } from "@/components/kazan/List";
import { useTranslation } from "@/i18n";

/** One choice of a PickerRow. */
interface PickerOption {
  id: string;
  label: string;
}

type PickerRowProps = {
  title: string;
  icon?: LucideIcon;
  iconTone?: string;
  /** The chosen option id; null shows `noneLabel`. */
  value: string | null;
  options: readonly PickerOption[];
  /** The label of "no choice", offered first in the menu. */
  noneLabel: string;
  onChange: (id: string | null) => void;
  disabled?: boolean;
};

/**
 * A list row showing one choice, which opens a menu of the options with
 * "no choice" first. It stands in for a native pull-down Select.
 */
function PickerRow({
  title,
  icon,
  iconTone,
  value,
  options,
  noneLabel,
  onChange,
  disabled = false,
}: PickerRowProps) {
  const { t } = useTranslation();
  const [open, setOpen] = React.useState<boolean>(false);
  const chosen: PickerOption | undefined = options.find((option) => option.id === value);

  const actions: ActionSheetAction[] = [
    { label: noneLabel, onPress: () => onChange(null) },
    ...options.map((option) => ({ label: option.label, onPress: () => onChange(option.id) })),
  ];

  return (
    <>
      <ListRow
        title={title}
        icon={icon}
        iconTone={iconTone}
        value={chosen?.label.trim() ?? noneLabel}
        disabled={disabled}
        onPress={disabled ? undefined : () => setOpen(true)}
      />
      <ActionSheet
        visible={open}
        onClose={() => setOpen(false)}
        title={title}
        actions={actions}
        cancelLabel={t("kitCancel")}
      />
    </>
  );
}

export { PickerRow };
export type { PickerOption, PickerRowProps };
