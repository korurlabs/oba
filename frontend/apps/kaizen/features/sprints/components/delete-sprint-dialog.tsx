/**
 * DeleteSprintDialog Component
 *
 * Dialog for deleting a sprint with ticket relocation options.
 * Follows the same pattern as CompleteSprintDialog.
 */

"use client";

import { useEffect, useMemo } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslations } from "next-intl";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Label } from "@/components/ui/label";
import { InlineAlert } from "@/components/ui/inline-alert";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Loader2, AlertCircle } from "lucide-react";
import { useSprints } from "@/features/sprints/hooks/use-sprints";
import { deleteSprintFormSchema, type DeleteSprintFormValues } from "@/features/sprints/schemas";
import type { TicketRelocationAction } from "@/features/tickets/schemas";

// ── Props ──────────────────────────────────────────────────────────

interface DeleteSprintDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  boardId: string;
  sprint: { id: string; name: string };
  onDeleteSprint: (ticketAction: TicketRelocationAction, targetSprintId?: string) => Promise<void>;
}

// ── Component ──────────────────────────────────────────────────────

export function DeleteSprintDialog({
  open,
  onOpenChange,
  boardId,
  sprint,
  onDeleteSprint,
}: DeleteSprintDialogProps) {
  const t = useTranslations();

  const form = useForm<DeleteSprintFormValues>({
    resolver: zodResolver(deleteSprintFormSchema),
    mode: "onChange",
    defaultValues: {
      ticketAction: "MOVE_TO_BACKLOG",
      targetSprintId: undefined,
    },
  });

  const ticketAction = form.watch("ticketAction");

  // Fetch available sprints on the same board (for MOVE_TO_SPRINT option)
  const { data: sprintsResponse } = useSprints(boardId, { enabled: open });
  const availableSprints = useMemo(() => {
    const sprints = sprintsResponse?.data ?? [];
    return sprints.filter((s) => s.id !== sprint.id && s.status !== "COMPLETED");
  }, [sprintsResponse?.data, sprint.id]);

  const hasAvailableSprints = availableSprints.length > 0;

  // Reset targetSprintId when ticketAction leaves MOVE_TO_SPRINT
  useEffect(() => {
    if (ticketAction !== "MOVE_TO_SPRINT") {
      form.setValue("targetSprintId", undefined);
    }
  }, [ticketAction, form]);

  // Reset form when dialog opens
  useEffect(() => {
    if (open) {
      form.reset({
        ticketAction: "MOVE_TO_BACKLOG",
        targetSprintId: undefined,
      });
    }
  }, [open, form]);

  const isSubmitting = form.formState.isSubmitting;

  const handleDelete = form.handleSubmit(async (values) => {
    await onDeleteSprint(
      values.ticketAction,
      values.ticketAction === "MOVE_TO_SPRINT" ? values.targetSprintId : undefined
    );
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="gap-4 sm:max-w-[500px]">
        <DialogHeader>
          <DialogTitle>
            {t("sprint.deleteTitle")}: {sprint.name}
          </DialogTitle>
          <DialogDescription>{t("sprint.deleteDescription")}</DialogDescription>
        </DialogHeader>

        <div className="flex flex-col gap-4 py-2">
          {/* Ticket action */}
          <div className="flex flex-col gap-2">
            <Label className="text-foreground text-sm font-semibold">
              {t("sprint.deleteAction")}
            </Label>

            <RadioGroup
              value={ticketAction}
              onValueChange={(v) =>
                form.setValue("ticketAction", v as TicketRelocationAction, {
                  shouldValidate: true,
                })
              }
            >
              {/* Move to Backlog */}
              <label
                htmlFor="delete-backlog"
                className="border-border hover:bg-accent flex cursor-pointer items-start gap-2 rounded-lg border p-3 transition-colors"
              >
                <RadioGroupItem value="MOVE_TO_BACKLOG" id="delete-backlog" className="mt-1" />
                <div className="flex-1">
                  <span className="font-medium">{t("sprint.completeDialog.moveToBacklog")}</span>
                  <p className="text-muted-foreground mt-1 text-xs">
                    {t("sprint.completeDialog.moveToBacklogDesc")}
                  </p>
                </div>
              </label>

              {/* Move to Sprint */}
              <label
                htmlFor="delete-sprint"
                className="border-border hover:bg-accent flex cursor-pointer items-start gap-2 rounded-lg border p-3 transition-colors"
              >
                <RadioGroupItem value="MOVE_TO_SPRINT" id="delete-sprint" className="mt-1" />
                <div className="flex-1">
                  <span className="font-medium">{t("sprint.completeDialog.moveToSprint")}</span>
                  <p className="text-muted-foreground mt-1 text-xs">
                    {t("sprint.completeDialog.moveToSprintDesc")}
                  </p>
                </div>
              </label>

              {/* Delete Tickets */}
              <label
                htmlFor="delete-tickets"
                className="border-border hover:bg-accent flex cursor-pointer items-start gap-2 rounded-lg border p-3 transition-colors"
              >
                <RadioGroupItem value="DELETE" id="delete-tickets" className="mt-1" />
                <div className="flex-1">
                  <span className="font-medium">{t("sprint.completeDialog.deleteTickets")}</span>
                  <p className="text-muted-foreground mt-1 text-xs">
                    {t("sprint.completeDialog.deleteTicketsDesc")}
                  </p>
                </div>
              </label>
            </RadioGroup>

            {/* Sprint picker when MOVE_TO_SPRINT */}
            {ticketAction === "MOVE_TO_SPRINT" && (
              <div className="flex flex-col gap-2 pl-6">
                {hasAvailableSprints ? (
                  <>
                    <Label className="text-xs">
                      {t("sprint.completeDialog.selectTargetSprint")}
                    </Label>
                    <Select
                      value={form.watch("targetSprintId") ?? ""}
                      onValueChange={(value) =>
                        form.setValue("targetSprintId", value, { shouldValidate: true })
                      }
                    >
                      <SelectTrigger>
                        <SelectValue placeholder={t("sprint.completeDialog.selectTargetSprint")} />
                      </SelectTrigger>
                      <SelectContent>
                        {availableSprints.map((s) => (
                          <SelectItem key={s.id} value={s.id}>
                            {s.name}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </>
                ) : (
                  <InlineAlert
                    variant="info"
                    icon={<AlertCircle />}
                    description={t("sprint.completeDialog.noPlannedSprints")}
                  />
                )}
              </div>
            )}
          </div>

          {/* Info notice */}
          <InlineAlert
            variant="info"
            icon={<AlertCircle />}
            description={t("sprint.deleteWarning")}
          />
        </div>

        <DialogFooter>
          <Button
            type="button"
            variant="ghost"
            onClick={() => onOpenChange(false)}
            disabled={isSubmitting}
          >
            {t("common.cancel")}
          </Button>
          <Button
            variant="destructive"
            onClick={handleDelete}
            disabled={isSubmitting || !form.formState.isValid}
          >
            {isSubmitting ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                {t("sprint.deleting")}
              </>
            ) : (
              t("sprint.deleteButton")
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
