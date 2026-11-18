import { onlineManager } from "@tanstack/react-query";
import * as React from "react";
import { Alert, type AlertButton } from "react-native";

import { selectedConflictBoards } from "@/components/sprints/sprintModel";
import { useConflictLines, useSeriesStopLines } from "@/components/sprints/TakeoverNotice";
import { toast } from "@/components/ui/toast";
import { useFetchStartPreview, useStartSprint } from "@/hooks/queries/sprints";
import { useTranslation } from "@/i18n";
import type { SprintStartPreview } from "@/lib/schemas/sprints";

/** The sprint to start: a planned sprint of the board. */
export interface StartableSprint {
  id: string;
  name: string;
}

/**
 * Starts a planned sprint of the board. The start preview is read at the tap:
 * with no other active sprint in the way the sprint starts at once; otherwise
 * a dialog names each sprint the start would close and each repeating series
 * that stops with them, and only "Move tickets and start" sends the takeover.
 * When the user is not admin of every listed board the dialog only explains
 * why the sprint cannot start.
 *
 * A plain start the server refuses (another admin started a sprint since the
 * preview) reads the preview again and opens the dialog when it now names
 * conflicts; the refusal itself is toasted by the app's error layer.
 */
export function useSprintStarter(boardId: string): {
  start: (sprint: StartableSprint) => void;
  pending: boolean;
} {
  const { t } = useTranslation();
  const fetchPreview = useFetchStartPreview(boardId);
  const startSprint = useStartSprint(boardId);
  const conflictLines = useConflictLines();
  const seriesStopLines = useSeriesStopLines();
  const [checking, setChecking] = React.useState<boolean>(false);
  const mutate = startSprint.mutate;

  const askTakeOver = React.useCallback(
    (sprint: StartableSprint, preview: SprintStartPreview, send: (takeOver: boolean) => void) => {
      const message: string = [
        t("sprintTakeoverWarning", {
          count: selectedConflictBoards(preview.conflicts),
          name: sprint.name,
        }),
        conflictLines(preview.conflicts).join("\n"),
        seriesStopLines(preview.conflicts).join("\n"),
        preview.canTakeOver ? null : t("sprintStartTakeoverBlocked"),
      ]
        .filter(Boolean)
        .join("\n\n");
      const buttons: AlertButton[] = preview.canTakeOver
        ? [
            { text: t("sprintStartTakeoverConfirm"), onPress: () => send(true) },
            { text: t("kitCancel"), style: "cancel" },
          ]
        : [{ text: t("sprintStartClose"), style: "cancel" }];
      Alert.alert(t("sprintStartTitle", { name: sprint.name }), message, buttons);
    },
    [conflictLines, seriesStopLines, t]
  );

  const check = React.useCallback(
    (sprint: StartableSprint, afterRefusal: boolean) => {
      const send = (takeOver: boolean) => {
        if (!onlineManager.isOnline()) {
          toast.error(t("offlineWriteBlocked"));
          return;
        }
        mutate(
          { sprintId: sprint.id, takeOver },
          {
            onSuccess: () => toast.success(t("sprintStarted", { name: sprint.name })),
            onError: (error: Error) => {
              // The app's error layer toasts the server's message.
              console.warn("[sprints] start failed", error.message);
              if (!takeOver) check(sprint, true);
            },
          }
        );
      };
      setChecking(true);
      fetchPreview(sprint.id)
        .then((preview: SprintStartPreview) => {
          if (preview.conflicts.length > 0) askTakeOver(sprint, preview, send);
          else if (!afterRefusal) send(false);
        })
        .catch((error: unknown) => {
          console.warn(
            "[sprints] start preview failed",
            error instanceof Error ? error.message : error
          );
          toast.error(t("sprintStartCheckError"), {
            action: { label: t("ticketRetry"), onPress: () => check(sprint, afterRefusal) },
          });
        })
        .finally(() => setChecking(false));
    },
    [askTakeOver, fetchPreview, mutate, t]
  );

  const start = React.useCallback(
    (sprint: StartableSprint) => {
      if (!onlineManager.isOnline()) {
        toast.error(t("offlineWriteBlocked"));
        return;
      }
      check(sprint, false);
    },
    [check, t]
  );

  return { start, pending: checking || startSprint.isPending };
}
