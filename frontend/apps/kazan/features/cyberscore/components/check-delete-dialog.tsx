"use client";

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
import { useDeleteCheck } from "../hooks/use-checks";
import type { Check } from "../schemas";

interface CheckDeleteDialogProps {
  /** The check to delete; kept after closing so the closing animation still names it. */
  check?: Check;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * Confirms the deletion of a check. Deleting recalculates the score, so the
 * confirmation says so. A failure is reported by the mutation's error toast.
 */
export function CheckDeleteDialog({ check, open, onOpenChange }: CheckDeleteDialogProps) {
  const t = useTranslations("cyberscore");
  const tCommon = useTranslations("common");
  const deleteCheck = useDeleteCheck();

  const handleOpenChange = (isOpen: boolean) => {
    // Closing under a running request would hide its outcome.
    if (deleteCheck.isPending) return;
    onOpenChange(isOpen);
  };

  const handleConfirm = (event: React.MouseEvent) => {
    // The dialog stays open until the server answers.
    event.preventDefault();
    if (!check) return;
    deleteCheck.mutate(check.id, { onSuccess: () => onOpenChange(false) });
  };

  return (
    <AlertDialog open={open} onOpenChange={handleOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{t("checks.deleteTitle")}</AlertDialogTitle>
          <AlertDialogDescription>
            {t("checks.deleteConfirm", { name: check?.name ?? "" })}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel disabled={deleteCheck.isPending}>
            {tCommon("cancel")}
          </AlertDialogCancel>
          <AlertDialogAction
            variant="destructive"
            disabled={deleteCheck.isPending || !check}
            onClick={handleConfirm}
          >
            {t("checks.delete")}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
