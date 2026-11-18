"use client";

import { useEffect, useState } from "react";
import { useTranslations } from "next-intl";
import { Card, CardContent, CardHeader } from "@oba/ui/sky/card";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { DataTable, TablePagination } from "@/components/data-table";
import { useIsOrganizationAdmin } from "@/hooks/use-is-organization-admin";
import { useOutreachColumns } from "../hooks/use-outreach-columns";
import { useOutreaches } from "../hooks/use-outreaches";
import { outreachErrorText } from "../lib/outreach-error";
import { relationOutreachesPageQuery, type OutreachListItem } from "../schemas";
import { useOutreachRowActions } from "./outreach-row-actions";

/**
 * A relation's reach-outs, newest first: to whom, their status, when and
 * from which mailbox they went. Every member lists and views them; an admin
 * also edits, sends and deletes the ones not sent yet. New ones start from
 * the Reach out button of a lead.
 */
export function OutreachesCard({ relationId }: { relationId: string }) {
  const t = useTranslations("outreaches");
  const tErrors = useTranslations("errors");
  const isAdmin = useIsOrganizationAdmin();
  const [page, setPage] = useState(1);
  const outreaches = useOutreaches(relationOutreachesPageQuery(relationId, page));
  const actions = useOutreachRowActions();
  const columns = useOutreachColumns({ showLead: false, ...actions });

  const rows = outreaches.data?.data ?? [];
  const pagination = outreaches.data?.pagination;
  // A delete that empties the last page moves back to the page that is now the last.
  const lastPage = pagination?.totalPages ?? 0;
  useEffect(() => {
    if (lastPage > 0 && page > lastPage) setPage(lastPage);
  }, [page, lastPage]);

  return (
    <Card role="region" aria-labelledby="outreaches" className="gap-4">
      <CardHeader>
        <h2 id="outreaches" className="text-lg leading-none">
          {t("cardTitle")}
        </h2>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {outreaches.isError && outreaches.data === undefined ? (
          <LoadError
            title={t("loadError")}
            message={outreachErrorText(outreaches.error, t, "errors.loadHint")}
            retryLabel={tErrors("tryAgain")}
            onRetry={() => void outreaches.refetch()}
          />
        ) : outreaches.data === undefined ? (
          <TableLoading label={t("loading")} />
        ) : rows.length === 0 ? (
          <p className="text-muted-foreground text-sm">
            {isAdmin ? t("cardEmpty") : t("cardEmptyMember")}
          </p>
        ) : (
          <>
            <DataTable<OutreachListItem>
              label={t("tableLabel")}
              columns={columns}
              data={rows}
              isFetching={outreaches.isFetching && outreaches.isPlaceholderData}
              getRowId={(outreach) => outreach.id}
              onRowClick={actions.onView}
            />
            {pagination && pagination.totalPages > 1 && (
              <TablePagination
                page={pagination.currentPage}
                totalPages={pagination.totalPages}
                onPageChange={setPage}
                label={t("pagination.label")}
                previousLabel={t("pagination.previous")}
                nextLabel={t("pagination.next")}
                summary={t("pagination.summary", {
                  page: pagination.currentPage,
                  pages: pagination.totalPages,
                  total: pagination.totalElements,
                })}
              />
            )}
          </>
        )}
      </CardContent>
      {actions.dialogs}
    </Card>
  );
}
