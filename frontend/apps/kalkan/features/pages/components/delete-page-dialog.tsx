"use client";

import { useTranslations } from "next-intl";
import { Button } from "@oba/ui/sky/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@oba/ui/sky/dialog";

interface DeletePageDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** The title of the page as the reader sees it. */
  title: string;
  /** How many live pages sit below it; the delete takes them along. */
  descendantCount: number;
  isDeleting: boolean;
  onConfirm: () => void;
}

/**
 * Asks before a page goes to the trash. The delete takes the whole subtree,
 * so the dialog says how many pages go with it. The failure of the delete is
 * the mutation's toast, so the dialog only closes when the caller says so.
 */
export function DeletePageDialog({
  open,
  onOpenChange,
  title,
  descendantCount,
  isDeleting,
  onConfirm,
}: DeletePageDialogProps) {
  const t = useTranslations("pageView.deleteDialog");

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("title")}</DialogTitle>
          <DialogDescription>
            {t("description", { title, count: descendantCount })}
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={isDeleting}>
            {t("cancel")}
          </Button>
          <Button variant="destructive" onClick={onConfirm} disabled={isDeleting}>
            {isDeleting ? t("deleting") : t("confirm")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
