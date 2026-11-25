"use client";

import { useCallback, useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { AlertTriangle, Plus, X } from "lucide-react";
import { SenseySearchInput } from "@oba/shared/components/sensey-search-input";
import { useRouter } from "@oba/shared/libs/i18n/navigation";
import { Button } from "@oba/ui/sky/button";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { DataTable } from "@/components/data-table";
import { ActionEmptyState, FilterLabel, PageHeading } from "@/components/page-states";
import { useIsOrganizationAdmin } from "@/hooks/use-is-organization-admin";
import { useCampaignColumns } from "../hooks/use-campaign-columns";
import { useCampaignFilterParams } from "../hooks/use-campaign-filter-params";
import { useCampaigns, useDeleteCampaign } from "../hooks/use-campaigns";
import { campaignErrorText } from "../lib/campaign-error";
import { hasActiveCampaignFilter, toCampaignQuery } from "../lib/campaign-filters";
import type { CampaignListItem } from "../schemas";
import { CampaignFormDialog } from "./campaign-form-dialog";

/**
 * The organization's campaigns with a name search in the URL. The list is
 * not paged: an organization keeps tens to hundreds of campaigns. Every
 * member creates and edits campaigns in a dialog; only an admin deletes.
 * The contents of a campaign are on its own page.
 */
export function CampaignsView() {
  const t = useTranslations("campaigns");
  const tErrors = useTranslations("errors");
  const router = useRouter();
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
                onClick={() => void setFilters({ search: null })}
              >
                <X aria-hidden="true" />
                {t("filters.clear")}
              </Button>
            }
          />
        ) : (
          <ActionEmptyState
            title={t("empty")}
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
