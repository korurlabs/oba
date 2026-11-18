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
import { SelectFieldStandalone } from "@oba/shared/features/form/components";
import { MILESTONE_LINK_MAX } from "../schemas";
import { useLinkMilestoneTickets } from "../hooks/use-milestones";
import { useMilestoneOptions } from "../hooks/use-milestone-options";

interface AttachMilestoneDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Board being viewed; its own and its related boards' OPEN milestones are offered. */
  boardId: string;
  ticketIds: string[];
  /** Runs after the tickets were attached. */
  onAttached?: () => void;
}

/** Attaches the selected tickets to one milestone, picked by id. */
export function AttachMilestoneDialog({
  open,
  onOpenChange,
  boardId,
  ticketIds,
  onAttached,
}: AttachMilestoneDialogProps) {
  const t = useTranslations("milestone");
  const tCommon = useTranslations("common");
  const [milestoneId, setMilestoneId] = useState("");
  const { options, milestones, isLoading } = useMilestoneOptions(boardId, { enabled: open });
  const linkTickets = useLinkMilestoneTickets();

  const selected = milestones.find((m) => m.id === milestoneId);
  const tooMany = ticketIds.length > MILESTONE_LINK_MAX;

  const handleOpenChange = (next: boolean) => {
    if (!next) setMilestoneId("");
    onOpenChange(next);
  };

  const handleAttach = () => {
    if (!selected) return;
    // The link endpoint lives on the milestone's own board, which may be a
    // related board rather than the one being viewed.
    linkTickets.mutate(
      { boardId: selected.boardId, milestoneId: selected.id, data: { ticketIds } },
      {
        onSuccess: () => {
          handleOpenChange(false);
          onAttached?.();
        },
      },
    );
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-[460px]">
        <DialogHeader>
          <DialogTitle>{t("attach.title")}</DialogTitle>
          <DialogDescription>{t("attach.description", { count: ticketIds.length })}</DialogDescription>
        </DialogHeader>

        <SelectFieldStandalone
          label={t("attach.milestone")}
          value={milestoneId}
          onValueChange={setMilestoneId}
          options={options}
          placeholder={t("attach.placeholder")}
          searchPlaceholder={tCommon("search")}
          isLoading={isLoading}
        />
        {!isLoading && options.length === 0 && (
          <p className="text-muted-foreground text-sm">{t("attach.noMilestones")}</p>
        )}
        {tooMany && (
          <p className="text-destructive text-sm">
            {t("attach.tooMany", { max: MILESTONE_LINK_MAX })}
          </p>
        )}

        <DialogFooter>
          <Button
            type="button"
            variant="ghost"
            onClick={() => handleOpenChange(false)}
            disabled={linkTickets.isPending}
          >
            {tCommon("cancel")}
          </Button>
          <Button
            type="button"
            onClick={handleAttach}
            disabled={!selected || tooMany || linkTickets.isPending}
          >
            {linkTickets.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            {t("attach.confirm")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
