"use client";

import type { MouseEvent } from "react";
import { useTranslations } from "next-intl";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { buttonVariants } from "@/components/ui/button";
import { useDeleteUpdate } from "../hooks/use-updates";

interface DeleteUpdateDialogProps {
  updateId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/** Confirms deleting the reader's own update. Ticket progress is not reverted. */
export function DeleteUpdateDialog({ updateId, open, onOpenChange }: DeleteUpdateDialogProps) {
  const t = useTranslations("updates");
  const tCommon = useTranslations("common");
  const deleteMutation = useDeleteUpdate();

  const handleDelete = (event: MouseEvent) => {
    // Kept open until the server answers, so a failure stays in view.
    event.preventDefault();
    deleteMutation.mutate(updateId, { onSuccess: () => onOpenChange(false) });
  };

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{t("delete.title")}</AlertDialogTitle>
          <AlertDialogDescription>{t("delete.description")}</AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel disabled={deleteMutation.isPending}>{tCommon("cancel")}</AlertDialogCancel>
          <AlertDialogAction
            className={buttonVariants({ variant: "destructive" })}
            disabled={deleteMutation.isPending}
            onClick={handleDelete}
          >
            {tCommon("delete")}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
