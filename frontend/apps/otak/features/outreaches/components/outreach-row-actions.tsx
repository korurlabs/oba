"use client";

import { useCallback, useState, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { useIsOrganizationAdmin } from "@/hooks/use-is-organization-admin";
import type { OutreachRowActions } from "../hooks/use-outreach-columns";
import { useCompleteOutreach, useDeleteOutreach } from "../hooks/use-outreaches";
import type { OutreachListItem } from "../schemas";
import { OutreachFormDialog } from "./outreach-form-dialog";
import { OutreachViewDialog } from "./outreach-view-dialog";
import { SendOutreachDialog } from "./send-outreach-dialog";

/**
 * The row actions of an outreach table and the dialogs they open. Every
 * member views; an admin also edits, sends a mail, marks a WhatsApp message
 * or a call as done, and deletes, as the server refuses a member's writes.
 * Render `dialogs` next to the table.
 */
export function useOutreachRowActions(): OutreachRowActions & { dialogs: ReactNode } {
  const t = useTranslations("outreaches");
  const isAdmin = useIsOrganizationAdmin();
  const deleteOutreach = useDeleteOutreach();
  const completeOutreach = useCompleteOutreach();
  const [viewingId, setViewingId] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [sending, setSending] = useState<OutreachListItem | null>(null);
  const [completing, setCompleting] = useState<OutreachListItem | null>(null);
  const [deleting, setDeleting] = useState<OutreachListItem | null>(null);

  const onView = useCallback((outreach: OutreachListItem) => setViewingId(outreach.id), []);
  const onEdit = useCallback((outreach: OutreachListItem) => setEditingId(outreach.id), []);
  const onSend = useCallback((outreach: OutreachListItem) => setSending(outreach), []);
  const onComplete = useCallback((outreach: OutreachListItem) => setCompleting(outreach), []);
  const onDelete = useCallback((outreach: OutreachListItem) => setDeleting(outreach), []);

  const dialogs = (
    <>
      <OutreachViewDialog
        outreachId={viewingId}
        onOpenChange={(open) => !open && setViewingId(null)}
      />
      {isAdmin && (
        <>
          <OutreachFormDialog
            outreachId={editingId}
            onOpenChange={(open) => !open && setEditingId(null)}
          />
          <SendOutreachDialog
            outreach={sending}
            onOpenChange={(open) => !open && setSending(null)}
          />
          <ConfirmDialog
            open={completing !== null}
            onOpenChange={(open) => !open && setCompleting(null)}
            title={t("completeTitle")}
            description={t("completeConfirm", {
              title: completing?.title ?? "",
              type: completing ? t(`types.${completing.type}`) : "",
            })}
            confirmLabel={t("completeAction")}
            cancelLabel={t("form.cancel")}
            onConfirm={() => {
              if (completing) {
                completeOutreach.mutate({ outreachId: completing.id });
              }
              setCompleting(null);
            }}
          />
          <ConfirmDialog
            open={deleting !== null}
            onOpenChange={(open) => !open && setDeleting(null)}
            title={t("deleteTitle")}
            description={t("deleteConfirm", { title: deleting?.title ?? "" })}
            confirmLabel={t("deleteAction")}
            cancelLabel={t("form.cancel")}
            destructive
            onConfirm={() => {
              if (deleting) {
                deleteOutreach.mutate({ outreachId: deleting.id });
              }
              setDeleting(null);
            }}
          />
        </>
      )}
    </>
  );

  return {
    onView,
    onEdit: isAdmin ? onEdit : null,
    onSend: isAdmin ? onSend : null,
    onComplete: isAdmin ? onComplete : null,
    onDelete: isAdmin ? onDelete : null,
    dialogs,
  };
}
