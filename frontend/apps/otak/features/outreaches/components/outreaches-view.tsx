"use client";

import { useMemo, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import { AlertTriangle, FileText, X } from "lucide-react";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { Button } from "@oba/ui/sky/button";
import { FilterPopover, type FilterCategory } from "@oba/ui/sky/filter-popover";
import { ListToolbar } from "@oba/ui/sky/list-toolbar";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { DataTable, TablePagination } from "@/components/data-table";
import { ActionEmptyState, PageHeading, useFilterPopoverLabels } from "@/components/page-states";
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
  const filterLabels = useFilterPopoverLabels();
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

  const filterCategories: FilterCategory[] = [
    {
      key: "status",
      label: t("filters.statusLabel"),
      mode: "single",
      value: filters.status,
      onChange: (value) => {
        const status = OutreachStatusSchema.safeParse(value);
        void setFilters({ status: status.success ? status.data : null, page: 1 });
      },
      options: OutreachStatusSchema.options.map((status) => ({
        value: status,
        label: t(`statuses.${status}`),
      })),
    },
    {
      key: "campaign",
      label: t("filters.campaignLabel"),
      mode: "single",
      value: filters.campaign,
      onChange: (campaign) => void setFilters({ campaign, page: 1 }),
      options: campaignOptions,
      searchable: true,
      isLoading: campaigns.isPending && campaigns.fetchStatus !== "idle",
      error: campaigns.isError ? t("filters.campaignsLoadError") : null,
    },
  ];

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

      <ListToolbar
        tabs={tabs}
        filters={
          <FilterPopover
            categories={filterCategories}
            onClear={() => void setFilters({ status: null, campaign: null, page: 1 })}
            labels={filterLabels}
          />
        }
      />

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
