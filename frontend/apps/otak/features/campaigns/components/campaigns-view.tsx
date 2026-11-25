"use client";

import { useCallback, useId, useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { AlertTriangle, Plus, X } from "lucide-react";
import { SenseySearchInput } from "@oba/shared/components/sensey-search-input";
import { useRouter } from "@oba/shared/libs/i18n/navigation";
import { Button } from "@oba/ui/sky/button";
import { NativeSelect } from "@oba/ui/sky/field";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { Tabs, TabsList, TabsTrigger } from "@oba/ui/sky/tabs";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { DataTable } from "@/components/data-table";
import { ActionEmptyState, FilterLabel, PageHeading } from "@/components/page-states";
import { useIsOrganizationAdmin } from "@/hooks/use-is-organization-admin";
import { useCampaignColumns } from "../hooks/use-campaign-columns";
import { useCampaignFilterParams } from "../hooks/use-campaign-filter-params";
import { useCampaigns, useDeleteCampaign } from "../hooks/use-campaigns";
import { campaignErrorText } from "../lib/campaign-error";
import {
  CAMPAIGN_TABS,
  hasActiveCampaignFilter,
  isCampaignTab,
  toCampaignQuery,
} from "../lib/campaign-filters";
import { CampaignChannelSchema, type CampaignListItem } from "../schemas";
import { CampaignFormDialog } from "./campaign-form-dialog";

/**
 * The organization's campaigns in a tab per status, with a channel filter
 * and a name search in the URL. The list is not paged: an organization keeps
 * tens to hundreds of campaigns. Every member creates and edits campaigns in
 * a dialog; only an admin deletes.
 */
export function CampaignsView() {
  const t = useTranslations("campaigns");
  const tErrors = useTranslations("errors");
  const router = useRouter();
  const channelId = useId();
  const isAdmin = useIsOrganizationAdmin();
  const [filters, setFilters] = useCampaignFilterParams();
  const query = useMemo(() => toCampaignQuery(filters), [filters]);
  const campaigns = useCampaigns(query);
  const deleteCampaign = useDeleteCampaign();
  const [formOpen, setFormOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [deleting, setDeleting] = useState<CampaignListItem | null>(null);

  const handleNew = useCallback(() => {
    setEditingId(null);
    setFormOpen(true);
  }, []);
  const handleEdit = useCallback((campaign: CampaignListItem) => {
    setEditingId(campaign.id);
    setFormOpen(true);
  }, []);
  const handleDelete = useCallback((campaign: CampaignListItem) => setDeleting(campaign), []);
  const columns = useCampaignColumns({
    onEdit: handleEdit,
    onDelete: isAdmin ? handleDelete : null,
  });
  const handleTabChange = useCallback(
    (value: string) => {
      if (!isCampaignTab(value)) return;
      void setFilters({ tab: value });
    },
    [setFilters]
  );

  const rows = useMemo(
    (): CampaignListItem[] => campaigns.data?.data ?? [],
    [campaigns.data?.data]
  );
  const isFiltered = hasActiveCampaignFilter(filters);

  const newButton = (
    <Button onClick={handleNew}>
      <Plus aria-hidden="true" />
      {t("new")}
    </Button>
  );

  return (
    <div className="flex flex-col gap-6">
      <PageHeading title={t("title")} description={t("subtitle")} actions={newButton} />

      <div className="flex flex-wrap items-end gap-3">
        <Tabs value={filters.tab} onValueChange={handleTabChange}>
          <TabsList aria-label={t("tabsLabel")}>
            {CAMPAIGN_TABS.map((tab) => (
              <TabsTrigger key={tab} value={tab}>
                {t(`tabs.${tab}`)}
              </TabsTrigger>
            ))}
          </TabsList>
        </Tabs>
        <FilterLabel
          label={t("filters.channelLabel")}
          htmlFor={channelId}
          className="w-full sm:w-44"
        >
          <NativeSelect
            id={channelId}
            value={filters.channel ?? ""}
            onChange={(event) => {
              const channel = CampaignChannelSchema.safeParse(event.target.value);
              void setFilters({ channel: channel.success ? channel.data : null });
            }}
            className="w-full"
          >
            <option value="">{t("filters.channelAll")}</option>
            {CampaignChannelSchema.options.map((channel) => (
              <option key={channel} value={channel}>
                {t(`channels.${channel}`)}
              </option>
            ))}
          </NativeSelect>
        </FilterLabel>
        <FilterLabel label={t("filters.searchLabel")} className="w-full sm:w-64">
          <SenseySearchInput
            value={filters.search}
            placeholder={t("filters.searchPlaceholder")}
            onSearchChange={(search) => void setFilters({ search: search ?? null })}
            className="w-full"
          />
        </FilterLabel>
      </div>

      {campaigns.isError && campaigns.data === undefined ? (
        <LoadError
          title={t("loadError")}
          message={campaignErrorText(campaigns.error, t, "errors.loadHint")}
          retryLabel={tErrors("tryAgain")}
          onRetry={() => void campaigns.refetch()}
        />
      ) : campaigns.data === undefined ? (
        <TableLoading label={t("loading")} />
      ) : rows.length === 0 ? (
        isFiltered ? (
          <ActionEmptyState
            title={t("emptyFiltered")}
            description={t("emptyFilteredHint")}
            action={
              <Button
                variant="outline"
                onClick={() => void setFilters({ channel: null, search: null })}
              >
                <X aria-hidden="true" />
                {t("filters.clear")}
              </Button>
            }
          />
        ) : (
          <ActionEmptyState
            title={t(`emptyByTab.${filters.tab}`)}
            description={t("emptyHint")}
            action={newButton}
          />
        )
      ) : (
        <>
          {campaigns.isError && (
            <p className="text-warning-text flex items-start gap-2 text-sm" role="status">
              <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
              {t("reloadError")}
            </p>
          )}
          <DataTable<CampaignListItem>
            label={t("tableLabel")}
            columns={columns}
            data={rows}
            isFetching={campaigns.isFetching && campaigns.isPlaceholderData}
            getRowId={(campaign) => campaign.id}
            onRowClick={(campaign) => router.push(`/campaigns/${campaign.id}`)}
          />
        </>
      )}

      <CampaignFormDialog campaignId={editingId} open={formOpen} onOpenChange={setFormOpen} />
      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(open) => !open && setDeleting(null)}
        title={t("deleteTitle")}
        description={t("deleteConfirm", { name: deleting?.name ?? "" })}
        confirmLabel={t("deleteAction")}
        cancelLabel={t("form.cancel")}
        destructive
        onConfirm={() => {
          if (deleting) deleteCampaign.mutate(deleting.id);
          setDeleting(null);
        }}
      />
    </div>
  );
}
