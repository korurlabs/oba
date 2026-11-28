"use client";

import { useCallback, useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { Plus } from "lucide-react";
import { Button } from "@oba/ui/sky/button";
import { ListToolbar } from "@oba/ui/sky/list-toolbar";
import { ListEmptyState } from "@oba/ui/sky/page-states";
import { SearchInput } from "@oba/ui/sky/search-input";
import { DataTable } from "@/components/data-table";
import { useClearSearchLabel, useListEmptyLabels } from "@/components/list-labels";
import type { LegalEntityListItem } from "../schemas";
import { useDeleteLegalEntity, useLegalEntities } from "../hooks/use-legal-entities";
import { useLegalEntityColumns } from "../hooks/use-legal-entity-columns";
import { useLegalEntityFilterParams } from "../hooks/use-legal-entity-filter-params";
import {
  CLEARED_LEGAL_ENTITY_FILTERS,
  filterLegalEntities,
  hasActiveLegalEntityFilter,
} from "../lib/legal-entity-filters";
import { LegalEntityFormDialog } from "./legal-entity-form-dialog";
import { OrganizationLoadError } from "./organization-load-error";

interface LegalEntitiesTabProps {
  organizationId: string;
  /** OWNER and ADMIN create, edit and delete; every other member only reads. */
  canEdit: boolean;
}

/**
 * The legal entities of the organization: the companies its documents are
 * issued by, sorted by short name, with a search in the URL. Every reader
 * gets the search; only an OWNER or ADMIN gets the create button.
 */
export function LegalEntitiesTab({ organizationId, canEdit }: LegalEntitiesTabProps) {
  const t = useTranslations("organization");
  const [filters, setFilters] = useLegalEntityFilterParams();
  const clearSearchLabel = useClearSearchLabel();
  const emptyLabels = useListEmptyLabels({
    emptyTitle: t("legalEntities.noLegalEntities"),
    emptyDescription: canEdit
      ? t("legalEntities.noLegalEntitiesDescription")
      : t("legalEntities.noLegalEntitiesReadOnly"),
    filteredTitle: t("legalEntities.filteredTitle"),
  });

  const { data: response, isLoading, isError, refetch } = useLegalEntities(organizationId);
  const { mutate: deleteEntity, isPending: isDeleting } = useDeleteLegalEntity(organizationId);
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<LegalEntityListItem | null>(null);

  const entities = useMemo(
    () => filterLegalEntities(response?.data ?? [], filters),
    [response?.data, filters]
  );

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

  const newButton = canEdit ? (
    <Button onClick={handleCreate}>
      <Plus aria-hidden="true" />
      {t("legalEntities.newLegalEntity")}
    </Button>
  ) : undefined;

  return (
    <div className="flex flex-col gap-4">
      <ListToolbar
        search={
          <SearchInput
            value={filters.search}
            onSearchChange={(search) => void setFilters({ search: search ?? null })}
            label={t("legalEntities.searchLabel")}
            clearLabel={clearSearchLabel}
          />
        }
        create={newButton}
      />

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
            <ListEmptyState
              filtered={hasActiveLegalEntityFilter(filters)}
              onClearFilters={() => void setFilters(CLEARED_LEGAL_ENTITY_FILTERS)}
              create={newButton}
              labels={emptyLabels}
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
