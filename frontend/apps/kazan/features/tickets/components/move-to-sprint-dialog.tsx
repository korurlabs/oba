"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { Loader2 } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useSprints } from "@/features/sprints/hooks/use-sprints";
import type { TicketListResponse } from "../schemas";
import {
  DEFAULT_SUB_TICKET_CHOICE,
  SubTicketChoice,
  hasSubTickets,
  type SubTicketMoveChoice,
} from "./sub-ticket-choice";

// ── Props ──────────────────────────────────────────────────────────

/**
 * What the user confirmed: the sprint (absent for a move to the backlog) and,
 * for a ticket with sub-tickets, which of them come along.
 */
export interface MoveTicketSelection {
  sprintId?: string;
  subTickets?: SubTicketMoveChoice;
}

interface MoveToSprintDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  boardId: string;
  currentSprintId?: string;
  /** The ticket being moved. When it has sub-tickets, the dialog asks which come along. */
  ticket?: TicketListResponse;
  /** `backlog` hides the sprint select: the dialog then only asks about the sub-tickets. */
  target?: "sprint" | "backlog";
  onConfirm: (selection: MoveTicketSelection) => void;
  isPending?: boolean;
}

// ── Component ──────────────────────────────────────────────────────

export function MoveToSprintDialog({
  open,
  onOpenChange,
  boardId,
  currentSprintId,
  ticket,
  target = "sprint",
  onConfirm,
  isPending,
}: MoveToSprintDialogProps) {
  const t = useTranslations();
  const [selectedSprintId, setSelectedSprintId] = useState<string>("");
  const [subTicketChoice, setSubTicketChoice] =
    useState<SubTicketMoveChoice>(DEFAULT_SUB_TICKET_CHOICE);

  const isSprintTarget = target === "sprint";
  const showSubTicketChoice = hasSubTickets(ticket);

  const { data: sprintsResponse, isLoading: sprintsLoading } = useSprints(boardId, {
    enabled: open && isSprintTarget,
  });

  const availableSprints = (sprintsResponse?.data ?? []).filter(
    (s) => s.id !== currentSprintId && s.status !== "COMPLETED"
  );

  const handleOpenChange = (nextOpen: boolean) => {
    if (!nextOpen) {
      setSelectedSprintId("");
      setSubTicketChoice(DEFAULT_SUB_TICKET_CHOICE);
    }
    onOpenChange(nextOpen);
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-[440px]">
        <DialogHeader>
          <DialogTitle>
            {isSprintTarget ? t("ticket.actions.moveToSprint") : t("ticket.actions.moveToBacklog")}
          </DialogTitle>
          <DialogDescription>
            {isSprintTarget
              ? t("ticket.actions.moveToSprintDescription")
              : t("ticket.actions.moveToBacklogDescription")}
          </DialogDescription>
        </DialogHeader>

        <div className="flex flex-col gap-4">
          {!isSprintTarget ? null : sprintsLoading ? (
            <div className="flex items-center justify-center py-4">
              <Loader2 className="text-muted-foreground h-5 w-5 animate-spin" />
            </div>
          ) : availableSprints.length === 0 ? (
            <p className="text-muted-foreground text-sm">
              {t("ticket.actions.noAvailableSprints")}
            </p>
          ) : (
            <Select value={selectedSprintId} onValueChange={setSelectedSprintId}>
              <SelectTrigger>
                <SelectValue placeholder={t("ticket.actions.selectSprint")} />
              </SelectTrigger>
              <SelectContent>
                {availableSprints.map((sprint) => (
                  <SelectItem key={sprint.id} value={sprint.id}>
                    {sprint.name}
                    {sprint.status === "ACTIVE" && (
                      <span className="kazan-grid-chip border-primary/50 text-korur-blue-strong ml-2">
                        {t("ticket.actions.activeLabel")}
                      </span>
                    )}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}

          {showSubTicketChoice && (
            <SubTicketChoice
              ticket={ticket}
              value={subTicketChoice}
              onChange={setSubTicketChoice}
              idPrefix="move-ticket"
              disabled={isPending}
            />
          )}
        </div>

        <DialogFooter>
          <Button
            variant="ghost"
            onClick={() => handleOpenChange(false)}
            disabled={isPending}
          >
            {t("common.cancel")}
          </Button>
          <Button
            onClick={() =>
              onConfirm({
                sprintId: isSprintTarget ? selectedSprintId : undefined,
                subTickets: showSubTicketChoice ? subTicketChoice : undefined,
              })
            }
            disabled={isPending || (isSprintTarget && !selectedSprintId)}
          >
            {isPending ? (
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
            ) : null}
            {t("ticket.actions.confirmMove")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
