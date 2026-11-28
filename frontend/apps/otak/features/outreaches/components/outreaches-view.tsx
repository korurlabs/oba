"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import { AlertTriangle } from "lucide-react";
import { SearchInput } from "@oba/ui/sky/search-input";
import { FilterPopover, type FilterCategory } from "@oba/ui/sky/filter-popover";
import { ListToolbar } from "@oba/ui/sky/list-toolbar";
import { ListEmptyState, LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { TablePagination } from "@oba/ui/sky/table-pagination";
import { PageHeading } from "@oba/ui/sky/page-heading";
import { DataTable } from "@/components/data-table";
import { useFilterPopoverLabels, useSearchClearLabel } from "@/components/list-labels";
import { useCampaignOptions } from "@/features/campaigns/hooks/use-campaigns";
import { useMailSenders } from "@/features/mail-senders/hooks/use-mail-senders";
import { useIsOrganizationAdmin } from "@/hooks/use-is-organization-admin";
import { useOutreachColumns } from "../hooks/use-outreach-columns";
import { useOutreachFilterParams } from "../hooks/use-outreach-filter-params";
import { useOutreaches } from "../hooks/use-outreaches";
import { outreachErrorText } from "../lib/outreach-error";
import {
  CLEARED_OUTREACH_FILTERS,
  CLEARED_OUTREACH_POPOVER_FILTERS,
  hasActiveOutreachFilter,
  toOutreachQuery,
} from "../lib/outreach-filters";
import { OutreachStatusSchema, OutreachTypeSchema, type OutreachListItem } from "../schemas";
import { useOutreachRowActions } from "./outreach-row-actions";
import { ReachOutButton } from "./reach-out-button";

/**
 * The organization's reach-outs, newest first, with a search, channel,
 * status, sender and campaign filters and paging in the URL. Each row links
 * to its lead. An admin reaches out from here by picking a lead first, or
 * from the lead's own page.
 */
export function OutreachesView() {
  const t = useTranslations("outreaches");
  const searchClearLabel = useSearchClearLabel();
  const tErrors = useTranslations("errors");
  const isAdmin = useIsOrganizationAdmin();
  const filterLabels = useFilterPopoverLabels();
  const campaigns = useCampaignOptions();
  const mailSenders = useMailSenders();
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
  const isFiltered = hasActiveOutreachFilter(filters);
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
  const filteredSenderId = filters.sender ?? "";
  const senderOptions = useMemo(() => {
    const options = (mailSenders.data?.data ?? []).map((sender) => ({
      value: sender.id,
      label: sender.mailAddress,
    }));
    if (filteredSenderId !== "" && !options.some((option) => option.value === filteredSenderId)) {
      // A link to a deleted sender, or one read before the list arrives,
      // shows its id so the filter can be cleared.
      options.push({ value: filteredSenderId, label: filteredSenderId });
    }
    return options;
  }, [mailSenders.data?.data, filteredSenderId]);

  const filterCategories: FilterCategory[] = [
    {
      key: "channel",
      label: t("filters.channelLabel"),
      mode: "single",
      value: filters.channel,
      onChange: (value) => {
        const channel = OutreachTypeSchema.safeParse(value);
        void setFilters({ channel: channel.success ? channel.data : null, page: 1 });
      },
      options: OutreachTypeSchema.options.map((type) => ({
        value: type,
        label: t(`types.${type}`),
      })),
    },
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
      key: "sender",
      label: t("filters.senderLabel"),
      mode: "single",
      value: filters.sender,
      onChange: (sender) => void setFilters({ sender, page: 1 }),
      options: senderOptions,
      searchable: true,
      isLoading: mailSenders.isPending && mailSenders.fetchStatus !== "idle",
      error: mailSenders.isError ? t("filters.sendersLoadError") : null,
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

  const reachOutButton = isAdmin ? <ReachOutButton /> : undefined;

  return (
    <div className="flex flex-col gap-6">
      <PageHeading title={t("title")} description={t("subtitle")} />

      <ListToolbar
        search={
          <SearchInput
            value={filters.search}
            label={t("filters.searchLabel")}
            clearLabel={searchClearLabel}
            placeholder={t("filters.searchPlaceholder")}
            onSearchChange={(search) => void setFilters({ search: search ?? null, page: 1 })}
          />
        }
        filters={
          <FilterPopover
            categories={filterCategories}
            onClear={() => void setFilters(CLEARED_OUTREACH_POPOVER_FILTERS)}
            labels={filterLabels}
          />
        }
        create={reachOutButton}
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
        <ListEmptyState
          filtered={isFiltered}
          onClearFilters={() => void setFilters(CLEARED_OUTREACH_FILTERS)}
          create={reachOutButton}
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
