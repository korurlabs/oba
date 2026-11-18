"use client";

import { useTranslations } from "next-intl";
import { Button } from "@oba/ui/sky/button";
import { ConfirmDialog } from "@oba/ui/sky/confirm-dialog";

interface InvitationActionDialogProps {
  trigger: string;
  title: string;
  description: string;
  confirmLabel: string;
  confirmVariant: "default" | "destructive" | "outline";
  onConfirm: () => void;
  disabled?: boolean;
}

/**
 * Asks before an invitation is accepted, declined or put off. The decision
 * hooks show their own failure as a toast, so the dialog closes once the
 * decision is sent.
 */
export function InvitationActionDialog({
  trigger,
  title,
  description,
  confirmLabel,
  confirmVariant,
  onConfirm,
  disabled,
}: InvitationActionDialogProps) {
  const t = useTranslations("pendingInvitations");

  return (
    <ConfirmDialog
      trigger={
        <Button variant={confirmVariant} size="sm" disabled={disabled}>
          {trigger}
        </Button>
      }
      title={title}
      description={description}
      confirmLabel={confirmLabel}
      destructive={confirmVariant === "destructive"}
      cancelLabel={t("cancel")}
      onConfirm={async () => onConfirm()}
    />
  );
}
