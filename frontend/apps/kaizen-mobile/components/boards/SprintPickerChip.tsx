import { Check, Repeat } from "lucide-react-native";
import * as React from "react";

import { sortSprintsForPicker } from "@/components/boards/boardModel";
import { useSprintStatusLabel } from "@/components/boards/SprintHeader";
import { ActionSheet, type ActionSheetAction } from "@/components/kaizen/ActionSheet";
import { Chip } from "@/components/ui/chip";
import { useTranslation } from "@/i18n";
import type { Sprint } from "@/lib/schemas/boards";

type SprintPickerChipProps = {
  sprints: readonly Sprint[];
  sprint: Sprint;
  onChange: (sprintId: string) => void;
};

/**
 * The All view's sprint choice: the chip names the sprint behind a repeat
 * mark, and the sheet lists every sprint with its state, active first.
 */
function SprintPickerChip({ sprints, sprint, onChange }: SprintPickerChipProps) {
  const { t } = useTranslation();
  const statusLabel = useSprintStatusLabel();
  const [open, setOpen] = React.useState<boolean>(false);

  const actions: ActionSheetAction[] = sortSprintsForPicker(sprints).map((item) => ({
    label: `${item.name} · ${statusLabel(item.status)}`,
    icon: item.id === sprint.id ? Check : undefined,
    onPress: () => {
      if (item.id !== sprint.id) onChange(item.id);
    },
  }));

  return (
    <>
      <Chip
        label={sprint.name}
        icon={Repeat}
        accessibilityLabel={`${t("boardSprintPicker")}: ${sprint.name}`}
        dropdown
        selected
        onPress={() => setOpen(true)}
      />
      <ActionSheet
        visible={open}
        onClose={() => setOpen(false)}
        title={t("boardSprintPicker")}
        actions={actions}
      />
    </>
  );
}

export { SprintPickerChip };
