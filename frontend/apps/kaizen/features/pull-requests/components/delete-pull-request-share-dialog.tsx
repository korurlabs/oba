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
import { useDeletePullRequestShare } from "../hooks/use-pull-request-shares";

interface DeletePullRequestShareDialogProps {
  shareId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/** Confirms deleting the reader's own share; its card leaves the PR channel for everyone. */
export function DeletePullRequestShareDialog({ shareId, open, onOpenChange }: DeletePullRequestShareDialogProps) {
  const t = useTranslations("pullRequests");
  const tCommon = useTranslations("common");
  const deleteMutation = useDeletePullRequestShare();

  const handleDelete = (event: MouseEvent) => {
    // Kept open until the server answers, so a failure stays in view.
    event.preventDefault();
    deleteMutation.mutate(shareId, { onSuccess: () => onOpenChange(false) });
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
