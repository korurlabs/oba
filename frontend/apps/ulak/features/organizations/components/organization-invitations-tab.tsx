"use client";

import { useCallback, useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { Plus } from "lucide-react";
import { Button } from "@oba/ui/sky/button";
import { FilterPopover, type FilterCategory } from "@oba/ui/sky/filter-popover";
import { ListToolbar } from "@oba/ui/sky/list-toolbar";
import { ListEmptyState } from "@oba/ui/sky/page-states";
import { SearchInput } from "@oba/ui/sky/search-input";
import {
  InvitationStatusSchema,
  type Invitation,
  type OrganizationRole,
} from "@oba/shared/features/organizations/schemas";
import { DataTable } from "@/components/data-table";
import {
  useClearSearchLabel,
  useFilterPopoverLabels,
  useListEmptyLabels,
} from "@/components/list-labels";
import { useInvitations, useRevokeInvitation } from "../hooks/use-invitations";
import { useInvitationColumns } from "../hooks/use-invitation-columns";
import { useInvitationFilterParams } from "../hooks/use-invitation-filter-params";
import {
  CLEARED_INVITATION_FILTERS,
  CLEARED_INVITATION_POPOVER_FILTERS,
  filterInvitations,
  hasActiveInvitationFilter,
} from "../lib/invitation-filters";
import { CreateInvitationDialog } from "./create-organization-invitation-dialog";

interface InvitationsTabProps {
  organizationId: string;
  canEdit: boolean;
  currentUserRole: OrganizationRole | undefined;
}

/**
 * The invitations of the organization, with a search on email and a status
 * filter in the URL; the API answers with every invitation, so the list is
 * narrowed here. Every member reads and searches the list; only an OWNER or
 * ADMIN invites and revokes.
 */
export function InvitationsTab({ organizationId, canEdit, currentUserRole }: InvitationsTabProps) {
  const t = useTranslations("organization");
  const [dialogOpen, setDialogOpen] = useState(false);
  const [filters, setFilters] = useInvitationFilterParams();
  const filterLabels = useFilterPopoverLabels();
  const clearSearchLabel = useClearSearchLabel();
  const emptyLabels = useListEmptyLabels({
    emptyTitle: t("invitations.noInvitations"),
    emptyDescription: canEdit
      ? t("invitations.noInvitationsDescription")
      : t("invitations.noInvitationsReadOnly"),
    filteredTitle: t("invitations.filteredTitle"),
  });

  const { data: invitationsResponse, isLoading } = useInvitations(organizationId);
  const { mutate: revokeInvitation, isPending: isRevoking } = useRevokeInvitation(organizationId);

  const invitations = useMemo(
    () => filterInvitations(invitationsResponse?.data ?? [], filters),
    [invitationsResponse?.data, filters]
  );

  // A dependency of the column memo: a new identity would remount every cell
  // and close an open revoke confirmation.
  const handleRevoke = useCallback(
    (invitationId: string) => {
      revokeInvitation(invitationId);
    },
    [revokeInvitation]
  );

  const columns = useInvitationColumns({
    canEdit,
    onRevoke: handleRevoke,
    isRevoking,
  });

  const filterCategories: FilterCategory[] = [
    {
      key: "status",
      label: t("invitations.status"),
      mode: "single",
      value: filters.status,
      onChange: (value) => {
        const status = InvitationStatusSchema.safeParse(value);
        void setFilters({ status: status.success ? status.data : null });
      },
      options: InvitationStatusSchema.options.map((value) => ({
        value,
        label: t(`invitations.statuses.${value}`),
      })),
    },
  ];

  const inviteButton = canEdit ? (
    <Button onClick={() => setDialogOpen(true)}>
      <Plus aria-hidden="true" />
      {t("invitations.invite")}
    </Button>
  ) : undefined;

  return (
    <div className="flex flex-col gap-4">
      <ListToolbar
        search={
          <SearchInput
            value={filters.search}
            onSearchChange={(search) => void setFilters({ search: search ?? null })}
            label={t("invitations.searchLabel")}
            clearLabel={clearSearchLabel}
          />
        }
        filters={
          <FilterPopover
            categories={filterCategories}
            onClear={() => void setFilters(CLEARED_INVITATION_POPOVER_FILTERS)}
            labels={filterLabels}
          />
        }
        create={inviteButton}
      />

      <DataTable<Invitation>
        columns={columns}
        data={invitations}
        isLoading={isLoading}
        loadingLabel={t("invitations.loading")}
        getRowId={(invitation: Invitation) => invitation.id}
        emptyMessage={
          <ListEmptyState
            filtered={hasActiveInvitationFilter(filters)}
            onClearFilters={() => void setFilters(CLEARED_INVITATION_FILTERS)}
            create={inviteButton}
            labels={emptyLabels}
          />
        }
      />

      {canEdit && (
        <CreateInvitationDialog
          organizationId={organizationId}
          currentUserRole={currentUserRole}
          open={dialogOpen}
          onOpenChange={setDialogOpen}
        />
      )}
    </div>
  );
}
