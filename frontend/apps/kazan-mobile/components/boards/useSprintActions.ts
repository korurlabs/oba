import { CircleCheck, Pencil, Play, Repeat } from "lucide-react-native";

import { useBoardLinks } from "@/components/boards/boardNavigation";
import type { ActionSheetAction } from "@/components/kazan/ActionSheet";
import { useCanManageSprints } from "@/components/sprints/useCanManageSprints";
import { useSprintStarter } from "@/components/sprints/useSprintStarter";
import { useTranslation } from "@/i18n";
import type { Sprint } from "@/lib/schemas/boards";

/**
 * The actions of a sprint the user may manage: Edit, Recurrence, and Start
 * or Complete by its state. Empty for a completed sprint, or when the user
 * may not manage the board's sprints, so callers hide their entry point.
 * The starter is returned too, so a screen's own Start button shares its
 * pending state with the menu's.
 */
export function useSprintActions(
  boardId: string,
  sprint: Sprint | null
): { actions: ActionSheetAction[]; starter: ReturnType<typeof useSprintStarter> } {
  const { t } = useTranslation();
  const { openEditSprint, openCompleteSprint } = useBoardLinks();
  const canManage: boolean = useCanManageSprints(boardId);
  const starter = useSprintStarter(boardId);

  if (!sprint || !canManage || sprint.status === "COMPLETED") {
    return { actions: [], starter };
  }
  const actions: ActionSheetAction[] = [
    {
      label: t("sprintEditTitle"),
      icon: Pencil,
      onPress: () => openEditSprint(boardId, sprint.id, "general"),
    },
    {
      label: t("boardRecurrence"),
      icon: Repeat,
      onPress: () => openEditSprint(boardId, sprint.id, "automation"),
    },
    sprint.status === "ACTIVE"
      ? {
          label: t("boardCompleteSprint"),
          icon: CircleCheck,
          onPress: () => openCompleteSprint(boardId, sprint.id),
        }
      : {
          label: t("sprintStartAction"),
          icon: Play,
          disabled: starter.pending,
          onPress: () => starter.start(sprint),
        },
  ];
  return { actions, starter };
}
