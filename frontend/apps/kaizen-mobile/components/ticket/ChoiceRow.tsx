import type { LucideIcon } from "lucide-react-native";
import * as React from "react";

import { ActionSheet, type ActionSheetAction } from "@/components/kaizen/ActionSheet";
import { ListRow } from "@/components/kaizen/List";
import { useTranslation } from "@/i18n";

/** One choice of a ChoiceRow. */
export interface Choice {
  id: string;
  /** The menu label; it may carry more detail than the row value. */
  label: string;
  /** What the row shows once this choice is made; the label without it. */
  value?: string;
}

type ChoiceRowProps = {
  title: string;
  icon?: LucideIcon;
  iconTone?: string;
  /** Any other leading element, in place of the icon. */
  leading?: React.ReactNode;
  /** Help under the title, such as what the chosen value does. */
  subtitle?: string;
  subtitleTone?: "muted" | "destructive" | "success";
  /** The chosen id; a value outside `choices` shows `placeholder`. */
  value: string | null;
  choices: readonly Choice[];
  onChange: (id: string) => void;
  /** Shown while nothing is chosen, such as during loading. */
  placeholder?: string;
  disabled?: boolean;
};

/**
 * A list row showing one required choice, which opens a menu of the choices
 * with the current one greyed out. Unlike the board pickers it has no "none"
 * entry: the field always has a value.
 */
export function ChoiceRow({
  title,
  icon,
  iconTone,
  leading,
  subtitle,
  subtitleTone,
  value,
  choices,
  onChange,
  placeholder,
  disabled = false,
}: ChoiceRowProps) {
  const { t } = useTranslation();
  const [open, setOpen] = React.useState<boolean>(false);
  const chosen: Choice | undefined = choices.find((choice) => choice.id === value);
  const actions: ActionSheetAction[] = choices.map((choice) => ({
    label: choice.label,
    disabled: choice.id === value,
    onPress: () => onChange(choice.id),
  }));

  return (
    <>
      <ListRow
        title={title}
        icon={icon}
        iconTone={iconTone}
        leading={leading}
        subtitle={subtitle}
        subtitleTone={subtitleTone}
        value={chosen ? (chosen.value ?? chosen.label) : placeholder}
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
