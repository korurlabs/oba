/**
 * Danger Zone Component
 *
 * Reusable danger zone section for destructive actions like delete.
 * Displays a warning box with a title, description, and destructive action button.
 * Shows a confirmation dialog before executing the destructive action.
 *
 * Supports an optional `customDialog` render prop to replace the default
 * AlertDialog with a custom dialog component (e.g. BoardDeleteDialog).
 */

"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { Trash2 } from "lucide-react";
import { Button, buttonVariants } from "@/components/ui/button";
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

interface DangerZoneProps {
  title: string;
  description: string;
  onDelete: () => void;
  disabled?: boolean;
  buttonText: string;
  /**
   * When provided, clicking the delete button opens this dialog instead
   * of the default AlertDialog confirmation. The dialog receives `open` and
   * `onOpenChange` so it can close itself.
   */
  customDialog?: (props: {
    open: boolean;
    onOpenChange: (open: boolean) => void;
  }) => React.ReactNode;
}

export function DangerZone({
  title,
  description,
  onDelete,
  disabled = false,
  buttonText,
  customDialog,
}: DangerZoneProps) {
  const t = useTranslations("common");
  const [open, setOpen] = useState(false);

  return (
    <div className="py-4">
      <div className="border-error bg-error-light dark:bg-secondary rounded-lg border p-4">
        <h4 className="text-foreground text-sm font-semibold">{title}</h4>
        <p className="text-muted-foreground mt-1 text-xs">{description}</p>
        <Button
          type="button"
          variant="destructive"
          className="mt-3 min-h-[44px] gap-2"
          onClick={() => setOpen(true)}
          disabled={disabled}
        >
          <Trash2 className="h-4 w-4" />
          <span>{buttonText}</span>
        </Button>
      </div>

      {customDialog ? (
        customDialog({ open, onOpenChange: setOpen })
      ) : (
        <AlertDialog open={open} onOpenChange={setOpen}>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>{t("confirmDeleteTitle")}</AlertDialogTitle>
              <AlertDialogDescription>{t("confirmDelete")}</AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel>{t("cancel")}</AlertDialogCancel>
              <AlertDialogAction
                className={buttonVariants({ variant: "destructive" })}
                onClick={onDelete}
              >
                {t("delete")}
              </AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
      )}
    </div>
  );
}
