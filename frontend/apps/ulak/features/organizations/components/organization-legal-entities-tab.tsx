"use client";

import { useCallback, useState } from "react";
import { useTranslations } from "next-intl";
import { Plus } from "lucide-react";
import { Button } from "@oba/ui/sky/button";
import { ListToolbar } from "@oba/ui/sky/list-toolbar";
import { EmptyState } from "@oba/ui/sky/page-states";
import { DataTable } from "@/components/data-table";
import type { LegalEntityListItem } from "../schemas";
import { useDeleteLegalEntity, useLegalEntities } from "../hooks/use-legal-entities";
import { useLegalEntityColumns } from "../hooks/use-legal-entity-columns";
import { LegalEntityFormDialog } from "./legal-entity-form-dialog";
import { OrganizationLoadError } from "./organization-load-error";

interface LegalEntitiesTabProps {
  organizationId: string;
  /** OWNER and ADMIN create, edit and delete; every other member only reads. */
  canEdit: boolean;
}

/**
 * The legal entities of the organization: the companies its documents are
 * issued by, sorted by short name.
 */
export function LegalEntitiesTab({ organizationId, canEdit }: LegalEntitiesTabProps) {
  const t = useTranslations("organization");

  const { data: response, isLoading, isError, refetch } = useLegalEntities(organizationId);
  const { mutate: deleteEntity, isPending: isDeleting } = useDeleteLegalEntity(organizationId);
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<LegalEntityListItem | null>(null);

  const entities = response?.data ?? [];

  // The handlers are dependencies of the column memo. A new identity rebuilds
  // every column and remounts every cell, which closes an open row menu or
  // dialog, so they are kept stable.
  const handleEdit = useCallback((entity: LegalEntityListItem) => {
    setEditing(entity);
    setFormOpen(true);
  }, []);

  const handleDelete = useCallback(
    (legalEntityId: string) => {
      deleteEntity(legalEntityId);
    },
    [deleteEntity]
  );

  const handleCreate = () => {
    setEditing(null);
    setFormOpen(true);
  };

  const columns = useLegalEntityColumns({
    canEdit,
    onEdit: handleEdit,
    onDelete: handleDelete,
    isDeleting,
  });

  return (
    <div className="flex flex-col gap-4">
      {canEdit && (
        <ListToolbar
          create={
            <Button size="sm" onClick={handleCreate}>
              <Plus aria-hidden="true" />
              {t("legalEntities.newLegalEntity")}
            </Button>
          }
        />
      )}

      {isError && !response ? (
        <OrganizationLoadError onRetry={() => void refetch()} />
      ) : (
        <DataTable<LegalEntityListItem>
          columns={columns}
          data={entities}
          isLoading={isLoading}
          loadingLabel={t("legalEntities.loading")}
          getRowId={(entity: LegalEntityListItem) => entity.id}
          emptyMessage={
            <EmptyState
              title={t("legalEntities.noLegalEntities")}
              description={
                canEdit
                  ? t("legalEntities.noLegalEntitiesDescription")
                  : t("legalEntities.noLegalEntitiesReadOnly")
              }
            />
          }
        />
      )}

      {canEdit && (
        <LegalEntityFormDialog
          organizationId={organizationId}
          legalEntity={editing}
          open={formOpen}
          onOpenChange={setFormOpen}
        />
      )}
    </div>
  );
}
