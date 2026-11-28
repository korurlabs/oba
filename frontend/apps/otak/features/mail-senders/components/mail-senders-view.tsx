"use client";

import { useCallback, useState, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import { AlertTriangle, Plus } from "lucide-react";
import { Button } from "@oba/ui/sky/button";
import { ListToolbar } from "@oba/ui/sky/list-toolbar";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { DataTable } from "@/components/data-table";
import { ActionEmptyState, PageHeading } from "@/components/page-states";
import { useIsOrganizationAdmin } from "@/hooks/use-is-organization-admin";
import { useMailSenderColumns } from "../hooks/use-mail-sender-columns";
import { useDeleteMailSender, useMailSenders } from "../hooks/use-mail-senders";
import { mailSenderErrorText } from "../lib/mail-sender-error";
import type { MailSenderListItem } from "../schemas";
import { MailSenderFormDialog } from "./mail-sender-form-dialog";

export interface MailSendersViewProps {
  /** The page's tabs, shown before the create button. */
  tabs?: ReactNode;
}

/**
 * The mailboxes of the Microsoft 365 tenant the organization sends its
 * outreach mail from, by mail address. An admin adds and deletes them; a
 * member sees the list read-only, as the server refuses a member's writes.
 */
export function MailSendersView({ tabs }: MailSendersViewProps) {
  const t = useTranslations("mailSenders");
  const tOutreaches = useTranslations("outreaches");
  const tErrors = useTranslations("errors");
  const isAdmin = useIsOrganizationAdmin();
  const mailSenders = useMailSenders();
  const deleteMailSender = useDeleteMailSender();
  const [formOpen, setFormOpen] = useState(false);
  const [deleting, setDeleting] = useState<MailSenderListItem | null>(null);

  const handleDelete = useCallback((mailSender: MailSenderListItem) => setDeleting(mailSender), []);
  const columns = useMailSenderColumns({ onDelete: isAdmin ? handleDelete : null });
  const rows = mailSenders.data?.data ?? [];

  const newButton = isAdmin ? (
    <Button onClick={() => setFormOpen(true)}>
      <Plus aria-hidden="true" />
      {t("new")}
    </Button>
  ) : undefined;

  return (
    <div className="flex flex-col gap-6">
      {/* The senders are a tab of the Outreaches page, so the page keeps its title. */}
      <PageHeading title={tOutreaches("title")} description={t("subtitle")} />

      {(tabs || newButton) && <ListToolbar tabs={tabs} create={newButton} />}

      {mailSenders.isError && mailSenders.data === undefined ? (
        <LoadError
          title={t("loadError")}
          message={mailSenderErrorText(mailSenders.error, t, "errors.loadHint")}
          retryLabel={tErrors("tryAgain")}
          onRetry={() => void mailSenders.refetch()}
        />
      ) : mailSenders.data === undefined ? (
        <TableLoading label={t("loading")} />
      ) : rows.length === 0 ? (
        <ActionEmptyState
          title={t("empty")}
          description={isAdmin ? t("emptyHint") : t("emptyHintMember")}
          action={newButton}
        />
      ) : (
        <>
          {mailSenders.isError && (
            <p className="text-warning-text flex items-start gap-2 text-sm" role="status">
              <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
              {t("reloadError")}
            </p>
          )}
          <DataTable<MailSenderListItem>
            label={t("tableLabel")}
            columns={columns}
            data={rows}
            getRowId={(mailSender) => mailSender.id}
          />
        </>
      )}

      {isAdmin && (
        <>
          <MailSenderFormDialog open={formOpen} onOpenChange={setFormOpen} />
          <ConfirmDialog
            open={deleting !== null}
            onOpenChange={(open) => !open && setDeleting(null)}
            title={t("deleteTitle")}
            description={t("deleteConfirm", { mailAddress: deleting?.mailAddress ?? "" })}
            confirmLabel={t("deleteAction")}
            cancelLabel={t("form.cancel")}
            destructive
            onConfirm={() => {
              if (deleting) deleteMailSender.mutate(deleting.id);
              setDeleting(null);
            }}
          />
        </>
      )}
    </div>
  );
}
