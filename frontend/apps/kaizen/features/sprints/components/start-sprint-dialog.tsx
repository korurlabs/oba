/**
 * StartSprintDialog Component
 *
 * Shown when a planned sprint is started while boards it is linked to still
 * run another active sprint. A plain start would leave such a board with two
 * active sprints, so the only way forward here is the takeover: close those
 * sprints, move their unfinished tickets into this one and start it. Cancel
 * changes nothing.
 */

"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { Loader2 } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import type { SprintStartPreviewResponse } from "../schemas";
import { SprintTakeoverNotice } from "./sprint-takeover-notice";

interface StartSprintDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  sprint: { id: string; name: string };
  preview: SprintStartPreviewResponse;
  /** Starts the sprint with takeover; rejects when the request fails. */
  onStartSprint: () => Promise<void>;
}

export function StartSprintDialog({
  open,
  onOpenChange,
  sprint,
  preview,
  onStartSprint,
}: StartSprintDialogProps) {
  const t = useTranslations();
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleStart = async () => {
    setIsSubmitting(true);
    try {
      await onStartSprint();
      onOpenChange(false);
    } catch (error) {
      // rule-10-no-op -- reason: `onStartSprint` resolves to useStartSprint's
      // `mutateAsync`, whose onError already surfaces the failure as a toast.
      // This catch only keeps the dialog open and stops the rejected promise
      // from becoming an unhandled rejection; the log keeps the stack.
      console.error("Failed to start sprint:", error);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[500px]">
        <DialogHeader>
          <DialogTitle className="pr-8">
            {t("sprint.takeover.startTitle", { name: sprint.name })}
          </DialogTitle>
        </DialogHeader>

        <SprintTakeoverNotice conflicts={preview.conflicts} sprintName={sprint.name} />

        <DialogFooter>
          <Button
            type="button"
            variant="ghost"
            onClick={() => onOpenChange(false)}
            disabled={isSubmitting}
          >
            {t("common.cancel")}
          </Button>
          <Button onClick={handleStart} disabled={isSubmitting || !preview.canTakeOver}>
            {isSubmitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            {t("sprint.takeover.moveAndStart")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
