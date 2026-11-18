"use client";

import { useEffect, useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { AlertCircle, ArrowRightLeft } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { InlineAlert } from "@/components/ui/inline-alert";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { SelectFieldStandalone } from "@oba/shared/features/form/components";
import type { SelectOption } from "@oba/shared/features/form/components";
import { useSprints } from "@/features/sprints/hooks/use-sprints";
import { TicketStatus } from "@/types/enums";
import type { TicketDetailResponse } from "../schemas";
import { TICKET_BACKLOG_SPRINT_VALUE } from "../schemas";
import { resolvePlacementOnBoard } from "../hooks/use-ticket-form";

interface MoveTicketBoardDialogProps {
  /** The ticket being edited; the dialog is open while a target board is set. */
  ticket: TicketDetailResponse;
  targetBoard: { id: string; name: string; prefix?: string | null } | null;
  /** `detachSubTickets`: leave the sub-tickets on the old board without a parent. */
  onConfirm: (placement: string, detachSubTickets: boolean) => void;
  onCancel: () => void;
}

/**
 * Asks where a ticket goes on the board picked in its Board field, and lists
 * what the move changes, before the choice is put into the form. A sprint is
 * linked to boards, so the ticket's sprint is offered only when the target
 * board shares it. A sub-ticket always shares its parent's board, so its
 * sub-tickets either move along or stay behind without a parent. The save
 * then moves the ticket.
 */
export function MoveTicketBoardDialog({
  ticket,
  targetBoard,
  onConfirm,
  onCancel,
}: MoveTicketBoardDialogProps) {
  const t = useTranslations();
  const open = !!targetBoard;
  const {
    data: sprintsResponse,
    isLoading: isSprintsLoading,
    isError: isSprintsError,
  } = useSprints(targetBoard?.id ?? "", { enabled: open });

  const openSprints = useMemo(
    () => (sprintsResponse?.data ?? []).filter((sprint) => sprint.status !== "COMPLETED"),
    [sprintsResponse?.data],
  );

  const [placement, setPlacement] = useState<string | undefined>(undefined);
  const [subTicketChoice, setSubTicketChoice] = useState<"move" | "detach">("move");
  useEffect(() => {
    setSubTicketChoice("move");
  }, [targetBoard?.id]);
  // Proposed once the target board's sprints are known, and again for each
  // board the dialog opens for.
  useEffect(() => {
    if (!open || !sprintsResponse?.data) {
      setPlacement(undefined);
      return;
    }
    setPlacement(resolvePlacementOnBoard(ticket.sprint, sprintsResponse.data));
  }, [open, targetBoard?.id, sprintsResponse?.data, ticket.sprint]);

  const placementOptions = useMemo((): SelectOption[] => {
    const sprintsGroup = t("ticket.fields.placementSprints");
    return [
      { value: TICKET_BACKLOG_SPRINT_VALUE, label: t("ticket.fields.placementBacklog") },
      ...openSprints.map((sprint) => ({
        value: sprint.id,
        label: sprint.name,
        group: sprintsGroup,
      })),
    ];
  }, [openSprints, t]);

  if (!targetBoard) return null;

  const boardName = targetBoard.name;
  const currentSprint = ticket.sprint ?? null;
  const isSprintShared =
    !!currentSprint && openSprints.some((sprint) => sprint.id === currentSprint.id);
  const toBacklog = placement === TICKET_BACKLOG_SPRINT_VALUE;
  const subTicketCount = ticket.subTickets?.length ?? 0;

  const detach = subTicketChoice === "detach";
  const oldBoardName = ticket.board?.name ?? "";
  const changes: string[] = [
    targetBoard.prefix
      ? t("ticket.moveBoard.changes.newKey", { key: ticket.key, prefix: targetBoard.prefix })
      : t("ticket.moveBoard.changes.newKeyNoPrefix", { key: ticket.key }),
  ];
  if (subTicketCount > 0) {
    changes.push(
      detach
        ? t("ticket.moveBoard.changes.subTicketsDetached", {
            count: subTicketCount,
            board: oldBoardName,
          })
        : t("ticket.moveBoard.changes.subTickets", { count: subTicketCount }),
    );
  }
  if (ticket.project) {
    changes.push(t("ticket.moveBoard.changes.project", { project: ticket.project.name }));
  }
  if (ticket.milestone) {
    changes.push(t("ticket.moveBoard.changes.milestone", { milestone: ticket.milestone.name }));
  }
  if (toBacklog && ticket.status !== TicketStatus.TODO) {
    changes.push(t("ticket.moveBoard.changes.backlogStatus"));
  }

  return (
    <Dialog open={open} onOpenChange={(next) => !next && onCancel()}>
      <DialogContent className="max-h-[80vh] max-w-lg overflow-y-auto">
        <DialogHeader>
          <DialogTitle>
            {t("ticket.moveBoard.title", { key: ticket.key, board: boardName })}
          </DialogTitle>
          <DialogDescription>{t("ticket.moveBoard.description")}</DialogDescription>
        </DialogHeader>

        <div className="flex flex-col gap-4">
          <div className="flex flex-col gap-2">
            <Label className="text-foreground text-sm font-semibold">
              {t("ticket.moveBoard.placementLabel", { board: boardName })}
            </Label>
            <p className="text-muted-foreground text-sm">
              {currentSprint
                ? isSprintShared
                  ? t("ticket.moveBoard.sprintShared", { sprint: currentSprint.name, board: boardName })
                  : t("ticket.moveBoard.sprintNotShared", { sprint: currentSprint.name, board: boardName })
                : t("ticket.moveBoard.fromBacklog", { board: boardName })}
            </p>
            {isSprintsError ? (
              <InlineAlert
                variant="danger"
                icon={<AlertCircle />}
                description={t("ticket.moveBoard.sprintsLoadFailed")}
              />
            ) : (
              <SelectFieldStandalone
                value={placement ?? ""}
                onValueChange={setPlacement}
                options={placementOptions}
                placeholder={t("ticket.actions.selectSprint")}
                searchPlaceholder={t("ticket.fields.searchSprint")}
                isLoading={isSprintsLoading}
              />
            )}
          </div>

          {subTicketCount > 0 && (
            <div className="flex flex-col gap-2">
              <Label className="text-foreground text-sm font-semibold">
                {t("ticket.moveBoard.subTicketsLabel", { count: subTicketCount })}
              </Label>
              <RadioGroup
                value={subTicketChoice}
                onValueChange={(value) => setSubTicketChoice(value as "move" | "detach")}
              >
                <label
                  htmlFor="move-board-sub-move"
                  className="border-border hover:bg-accent flex cursor-pointer items-start gap-2 rounded-lg border p-3 transition-colors"
                >
                  <RadioGroupItem value="move" id="move-board-sub-move" className="mt-1" />
                  <div className="flex-1">
                    <span className="font-medium">
                      {t("ticket.moveBoard.subTicketsMove", { board: boardName })}
                    </span>
                    <p className="text-muted-foreground mt-1 text-xs">
                      {t("ticket.moveBoard.subTicketsMoveDesc", { prefix: targetBoard.prefix ?? "" })}
                    </p>
                  </div>
                </label>
                <label
                  htmlFor="move-board-sub-detach"
                  className="border-border hover:bg-accent flex cursor-pointer items-start gap-2 rounded-lg border p-3 transition-colors"
                >
                  <RadioGroupItem value="detach" id="move-board-sub-detach" className="mt-1" />
                  <div className="flex-1">
                    <span className="font-medium">
                      {t("ticket.moveBoard.subTicketsDetach", { board: oldBoardName })}
                    </span>
                    <p className="text-muted-foreground mt-1 text-xs">
                      {t("ticket.moveBoard.subTicketsDetachDesc")}
                    </p>
                  </div>
                </label>
              </RadioGroup>
            </div>
          )}

          <div className="flex flex-col gap-2">
            <Label className="text-foreground text-sm font-semibold">
              {t("ticket.moveBoard.changesLabel")}
            </Label>
            <ul className="text-muted-foreground list-disc space-y-1 pl-5 text-sm">
              {changes.map((change) => (
                <li key={change}>{change}</li>
              ))}
            </ul>
          </div>
        </div>

        <DialogFooter>
          <Button type="button" variant="ghost" onClick={onCancel}>
            {t("common.cancel")}
          </Button>
          <Button
            type="button"
            onClick={() => placement && onConfirm(placement, detach)}
            disabled={!placement || isSprintsError}
          >
            <ArrowRightLeft className="mr-2 h-4 w-4" />
            {t("ticket.moveBoard.confirm", { board: boardName })}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
