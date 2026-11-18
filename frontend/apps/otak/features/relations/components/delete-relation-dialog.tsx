"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { Trash2 } from "lucide-react";
import { Button } from "@oba/ui/sky/button";
import { ConfirmDialog } from "@/components/confirm-dialog";

interface DeleteRelationDialogProps {
  relationName: string;
  isDeleting: boolean;
  onConfirm: () => void;
}

/** Asks before a relation is removed with its contacts; it cannot be undone. */
export function DeleteRelationDialog({
  relationName,
  isDeleting,
  onConfirm,
}: DeleteRelationDialogProps) {
  const t = useTranslations("relations.detail");
  const [open, setOpen] = useState(false);

  return (
    <>
      {/* Outlined, not filled: the heading keeps one filled action, and this one asks first. */}
      <Button
        variant="outline"
        disabled={isDeleting}
        onClick={() => setOpen(true)}
        className="text-destructive hover:text-destructive"
      >
        <Trash2 aria-hidden="true" />
        {isDeleting ? t("deleting") : t("delete")}
      </Button>
      <ConfirmDialog
        open={open}
        onOpenChange={setOpen}
        title={t("deleteTitle")}
        description={t("deleteConfirm", { name: relationName })}
        confirmLabel={t("delete")}
        cancelLabel={t("cancel")}
        destructive
        onConfirm={() => {
          setOpen(false);
          onConfirm();
        }}
      />
    </>
  );
}
