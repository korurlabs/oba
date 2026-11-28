"use client";

import { useCallback, useMemo, useState, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import { AlertTriangle, Plus } from "lucide-react";
import { SearchInput } from "@oba/ui/sky/search-input";
import { Button } from "@oba/ui/sky/button";
import { ListToolbar } from "@oba/ui/sky/list-toolbar";
import { ListEmptyState, LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { PageHeading } from "@oba/ui/sky/page-heading";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { DataTable } from "@/components/data-table";
import { useSearchClearLabel } from "@/components/list-labels";
import { useIsOrganizationAdmin } from "@/hooks/use-is-organization-admin";
import { useMailSenderColumns } from "../hooks/use-mail-sender-columns";
import { useMailSenderFilterParams } from "../hooks/use-mail-sender-filter-params";
import { useDeleteMailSender, useMailSenders } from "../hooks/use-mail-senders";
import { mailSenderErrorText } from "../lib/mail-sender-error";
import { filterMailSenders, hasActiveMailSenderFilter } from "../lib/mail-sender-filters";
import type { MailSenderListItem } from "../schemas";
import { MailSenderFormDialog } from "./mail-sender-form-dialog";

export interface MailSendersViewProps {
  /** The Mail page's tabs, shown between the heading and the toolbar. */
  tabs?: ReactNode;
}

/**
 * The mailboxes of the Microsoft 365 tenant the organization sends its
 * outreach mail from, by mail address. An admin adds and deletes them; a
 * member sees the list read-only, as the server refuses a member's writes.
 * The search on the mail address is in the URL and narrows the list in the
 * browser, as the server returns every sender at once.
 */
export function MailSendersView({ tabs }: MailSendersViewProps) {
  const t = useTranslations("mailSenders");
  const searchClearLabel = useSearchClearLabel();
  const tMail = useTranslations("mailPage");
  const tErrors = useTranslations("errors");
  const isAdmin = useIsOrganizationAdmin();
  const [filters, setFilters] = useMailSenderFilterParams();
  const mailSenders = useMailSenders();
  const deleteMailSender = useDeleteMailSender();
  const [formOpen, setFormOpen] = useState(false);
  const [deleting, setDeleting] = useState<MailSenderListItem | null>(null);

  const handleDelete = useCallback((mailSender: MailSenderListItem) => setDeleting(mailSender), []);
  const columns = useMailSenderColumns({ onDelete: isAdmin ? handleDelete : null });
  const rows = useMemo(
    () => filterMailSenders(mailSenders.data?.data ?? [], filters),
    [mailSenders.data?.data, filters]
  );
  const isFiltered = hasActiveMailSenderFilter(filters);

  const newButton = isAdmin ? (
    <Button onClick={() => setFormOpen(true)}>
      <Plus aria-hidden="true" />
      {t("new")}
    </Button>
  ) : undefined;

  return (
    <div className="flex flex-col gap-6">
      <PageHeading title={tMail("title")} description={t("subtitle")} />

      {tabs}

      <ListToolbar
        search={
          <SearchInput
            value={filters.search}
            label={t("filters.searchLabel")}
            clearLabel={searchClearLabel}
            placeholder={t("filters.searchPlaceholder")}
            onSearchChange={(search) => void setFilters({ search: search ?? null })}
          />
        }
        create={newButton}
      />

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
        <ListEmptyState
          filtered={isFiltered}
          onClearFilters={() => void setFilters({ search: null })}
          create={newButton}
          labels={{
            emptyTitle: t("empty"),
            emptyDescription: isAdmin ? t("emptyHint") : t("emptyHintMember"),
            filteredTitle: t("emptyFiltered"),
            filteredDescription: t("emptyFilteredHint"),
            clearFilters: t("filters.clear"),
          }}
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
