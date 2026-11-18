import * as React from "react";
import { Alert } from "react-native";

import { toast } from "@/components/ui/toast";
import {
  useCloseMilestone,
  useDeleteMilestone,
  useReopenMilestone,
  type MilestoneTarget,
} from "@/hooks/queries/boardSettings";
import { useOnlineWriteGuard } from "@/hooks/useOnlineWriteGuard";
import { useTranslation } from "@/i18n";
import { isValidationError } from "@/lib/api";

/** The milestone an action applies to: where it lives and the name its delete question names. */
export interface MilestoneActionTarget extends MilestoneTarget {
  name: string;
}

/**
 * Surfaces a failed milestone write. The app's error layer toasts the
 * server's message, as the web hooks do, except for a field validation
 * refusal, which it leaves to a form; these actions have no form, so that
 * message is toasted here.
 */
function reportFailure(action: string, error: Error): void {
  if (isValidationError(error)) toast.error(error.message);
  console.warn(`[milestones] ${action} failed`, error.message);
}

/**
 * Close, Reopen and Delete of a milestone, shared by the Milestones list's
 * long-press menu and the Milestone sheet. Close and Reopen run at once;
 * Delete asks the web's question first. Each success toasts the web's
 * message.
 */
export function useMilestoneActions() {
  const { t } = useTranslation();
  const online = useOnlineWriteGuard();
  const closeMilestone = useCloseMilestone();
  const reopenMilestone = useReopenMilestone();
  const deleteMilestone = useDeleteMilestone();
  const { mutate: closeMutate } = closeMilestone;
  const { mutate: reopenMutate } = reopenMilestone;
  const { mutate: deleteMutate } = deleteMilestone;

  const close = React.useCallback(
    (target: MilestoneActionTarget) => {
      if (!online()) return;
      closeMutate(
        { boardId: target.boardId, milestoneId: target.milestoneId },
        {
          onSuccess: () => toast.success(t("boardMilestonesClosed")),
          onError: (error: Error) => reportFailure("close", error),
        }
      );
    },
    [online, closeMutate, t]
  );

  const reopen = React.useCallback(
    (target: MilestoneActionTarget) => {
      if (!online()) return;
      reopenMutate(
        { boardId: target.boardId, milestoneId: target.milestoneId },
        {
          onSuccess: () => toast.success(t("boardMilestonesReopened")),
          onError: (error: Error) => reportFailure("reopen", error),
        }
      );
    },
    [online, reopenMutate, t]
  );

  /**
   * Asks the web's question with Cancel and Delete, then deletes. `onStart`
   * runs as the request leaves, `onDeleted` after it succeeded and
   * `onFailed` after it failed, so an open sheet can stop reading the
   * milestone while it goes and close afterwards.
   */
  const confirmDelete = React.useCallback(
    (
      target: MilestoneActionTarget,
      callbacks: { onStart?: () => void; onDeleted?: () => void; onFailed?: () => void } = {}
    ) => {
      if (!online()) return;
      Alert.alert(
        t("boardMilestonesDeleteTitle"),
        t("boardMilestonesDeleteBody", { name: target.name }),
        [
          { text: t("kitCancel"), style: "cancel" },
          {
            text: t("boardOptionsDelete"),
            style: "destructive",
            onPress: () => {
              callbacks.onStart?.();
              deleteMutate(
                { boardId: target.boardId, milestoneId: target.milestoneId },
                {
                  onSuccess: () => {
                    toast.success(t("boardMilestonesDeleted"));
                    callbacks.onDeleted?.();
                  },
                  onError: (error: Error) => {
                    reportFailure("delete", error);
                    callbacks.onFailed?.();
                  },
                }
              );
            },
          },
        ]
      );
    },
    [online, deleteMutate, t]
  );

  return {
    close,
    reopen,
    confirmDelete,
    isClosing: closeMilestone.isPending,
    isReopening: reopenMilestone.isPending,
    isDeleting: deleteMilestone.isPending,
  };
}
