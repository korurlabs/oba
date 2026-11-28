"use client";

import { useCallback, useState } from "react";
import { useTranslations } from "next-intl";
import { Plus } from "lucide-react";
import { Button } from "@oba/ui/sky/button";
import { Card, CardContent, CardHeader } from "@oba/ui/sky/card";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { DataTable } from "@/components/data-table";
import { useCampaignContentColumns } from "../hooks/use-campaign-content-columns";
import { useCampaignContents, useDeleteCampaignContent } from "../hooks/use-campaigns";
import { campaignErrorText } from "../lib/campaign-error";
import type { CampaignContent } from "../schemas";
import { CampaignContentFormDialog } from "./campaign-content-form-dialog";

/**
 * The contents of a campaign, soonest planned first, with a dialog to add,
 * edit and mark one as published. Every member adds, edits and deletes
 * contents.
 */
export function CampaignContentTable({ campaignId }: { campaignId: string }) {
  const t = useTranslations("campaigns");
  const tErrors = useTranslations("errors");
  const contents = useCampaignContents(campaignId);
  const deleteContent = useDeleteCampaignContent();
  const [dialogOpen, setDialogOpen] = useState(false);
  // The content the dialog edits; null while it creates a new one.
  const [editing, setEditing] = useState<CampaignContent | null>(null);
  const [deleting, setDeleting] = useState<CampaignContent | null>(null);

  const handleNew = () => {
    setEditing(null);
    setDialogOpen(true);
  };
  const handleEdit = useCallback((content: CampaignContent) => {
    setEditing(content);
    setDialogOpen(true);
  }, []);
  const handleDelete = useCallback((content: CampaignContent) => setDeleting(content), []);
  const columns = useCampaignContentColumns({ onEdit: handleEdit, onDelete: handleDelete });

  const rows = contents.data?.data ?? [];

  return (
    <Card role="region" aria-labelledby="campaign-contents" className="gap-4">
      <CardHeader className="flex flex-wrap items-center justify-between gap-2">
        <h2 id="campaign-contents" className="text-lg leading-none">
          {t("detail.contents")}
        </h2>
        <Button size="sm" onClick={handleNew}>
          <Plus aria-hidden="true" />
          {t("detail.newContent")}
        </Button>
      </CardHeader>
      <CardContent>
        {contents.isError && contents.data === undefined ? (
          <LoadError
            title={t("detail.contentsLoadError")}
            message={campaignErrorText(contents.error, t, "errors.loadHint")}
            retryLabel={tErrors("tryAgain")}
            onRetry={() => void contents.refetch()}
          />
        ) : contents.data === undefined ? (
          <TableLoading label={t("detail.contentsLoading")} />
        ) : rows.length === 0 ? (
          <p className="text-muted-foreground text-sm" role="status">
            {t("detail.noContents")}
          </p>
        ) : (
          <DataTable<CampaignContent>
            label={t("detail.contents")}
            columns={columns}
            data={rows}
            getRowId={(content) => content.id}
            onRowClick={handleEdit}
          />
        )}
      </CardContent>

      <CampaignContentFormDialog
        campaignId={campaignId}
        content={editing}
        onSaved={setEditing}
        open={dialogOpen}
        onOpenChange={setDialogOpen}
      />
      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(open) => !open && setDeleting(null)}
        title={t("contentDeleteTitle")}
        description={t("contentDeleteConfirm")}
        confirmLabel={t("deleteAction")}
        cancelLabel={t("form.cancel")}
        destructive
        onConfirm={() => {
          if (deleting) deleteContent.mutate({ campaignId, contentId: deleting.id });
          setDeleting(null);
        }}
      />
    </Card>
  );
}
