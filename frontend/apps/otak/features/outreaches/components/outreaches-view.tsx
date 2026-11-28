"use client";

import { useId, useMemo, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import { AlertTriangle, FileText, X } from "lucide-react";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { Button } from "@oba/ui/sky/button";
import { NativeSelect } from "@oba/ui/sky/field";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { DataTable, TablePagination } from "@/components/data-table";
import { ActionEmptyState, FilterLabel, PageHeading } from "@/components/page-states";
import { useCampaignOptions } from "@/features/campaigns/hooks/use-campaigns";
import { useIsOrganizationAdmin } from "@/hooks/use-is-organization-admin";
import { useOutreachColumns } from "../hooks/use-outreach-columns";
import { useOutreachFilterParams } from "../hooks/use-outreach-filter-params";
import { useOutreaches } from "../hooks/use-outreaches";
import { outreachErrorText } from "../lib/outreach-error";
import { toOutreachQuery } from "../lib/outreach-filters";
import { OutreachStatusSchema, type OutreachListItem } from "../schemas";
import { useOutreachRowActions } from "./outreach-row-actions";

export interface OutreachesViewProps {
  /** The page's tabs, shown before the filters. */
  tabs?: ReactNode;
}

/**
 * The organization's reach-outs, newest first, with status and campaign
 * filters and paging in the URL. Each row links to its lead. New reach-outs start on a
 * lead's page; the reusable mails themselves are the mail templates.
 */
export function OutreachesView({ tabs }: OutreachesViewProps) {
  const t = useTranslations("outreaches");
  const tErrors = useTranslations("errors");
  const isAdmin = useIsOrganizationAdmin();
  const statusId = useId();
  const campaignId = useId();
  const campaigns = useCampaignOptions();
  const [filters, setFilters] = useOutreachFilterParams();
  const query = useMemo(() => toOutreachQuery(filters), [filters]);
  const outreaches = useOutreaches(query);
  const actions = useOutreachRowActions();
  const columns = useOutreachColumns({ showLead: true, ...actions });

  const rows = useMemo(
    (): OutreachListItem[] => outreaches.data?.data ?? [],
    [outreaches.data?.data]
  );
  const pagination = outreaches.data?.pagination;
  const isFiltered = !!filters.status || !!filters.campaign;
  const filteredCampaignId = filters.campaign ?? "";
  const campaignOptions = useMemo(() => {
    const options = [...(campaigns.data ?? [])];
    if (
      filteredCampaignId !== "" &&
      !options.some((option) => option.value === filteredCampaignId)
    ) {
      // The options hold every campaign; a link to a deleted one, or one read
      // before the list arrives, shows its id so the filter can be cleared.
      options.push({ value: filteredCampaignId, label: filteredCampaignId });
    }
    return options;
  }, [campaigns.data, filteredCampaignId]);

  const templatesLink = (
    <Button asChild variant="outline">
      <Link href="/mail">
        <FileText aria-hidden="true" />
        {t("mailTemplates")}
      </Link>
    </Button>
  );

  return (
    <div className="flex flex-col gap-6">
      <PageHeading title={t("title")} description={t("subtitle")} actions={templatesLink} />

      <div className="flex flex-wrap items-end gap-3">
        {tabs}
        <FilterLabel label={t("filters.statusLabel")} htmlFor={statusId} className="w-full sm:w-48">
          <NativeSelect
            id={statusId}
            value={filters.status ?? ""}
            onChange={(event) => {
              const status = OutreachStatusSchema.safeParse(event.target.value);
              void setFilters({ status: status.success ? status.data : null, page: 1 });
            }}
            className="w-full"
          >
            <option value="">{t("filters.statusAll")}</option>
            {OutreachStatusSchema.options.map((status) => (
              <option key={status} value={status}>
                {t(`statuses.${status}`)}
              </option>
            ))}
          </NativeSelect>
        </FilterLabel>
        <FilterLabel
          label={t("filters.campaignLabel")}
          htmlFor={campaignId}
          className="w-full sm:w-48"
        >
          <NativeSelect
            id={campaignId}
            value={filteredCampaignId}
            disabled={campaigns.isPending && campaigns.fetchStatus !== "idle"}
            onChange={(event) => void setFilters({ campaign: event.target.value || null, page: 1 })}
            className="w-full"
          >
            <option value="">{t("filters.campaignAll")}</option>
            {campaignOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </NativeSelect>
        </FilterLabel>
        {campaigns.isError && (
          <p className="text-warning-text w-full text-xs" role="status">
            {t("filters.campaignsLoadError")}
          </p>
        )}
      </div>

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
        isFiltered ? (
          <ActionEmptyState
            title={t("emptyFiltered")}
            description={t("emptyFilteredHint")}
            action={
              <Button
                variant="outline"
                onClick={() => void setFilters({ status: null, campaign: null, page: 1 })}
              >
                <X aria-hidden="true" />
                {t("filters.clear")}
              </Button>
            }
          />
        ) : (
          <ActionEmptyState
            title={t("empty")}
            description={isAdmin ? t("emptyHint") : t("emptyHintMember")}
            action={
              <Button asChild variant="outline">
                <Link href="/relations">{t("goToLeads")}</Link>
              </Button>
            }
          />
        )
      ) : (
        <>
          {outreaches.isError && (
            <p className="text-warning-text flex items-start gap-2 text-sm" role="status">
              <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
              {t("reloadError")}
            </p>
          )}
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
              onPageChange={(page) => void setFilters({ page })}
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

      {actions.dialogs}
    </div>
  );
}
