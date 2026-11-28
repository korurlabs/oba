"use client";

import { useCallback, useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { Plus } from "lucide-react";
import { Button } from "@oba/ui/sky/button";
import { FilterPopover, type FilterCategory } from "@oba/ui/sky/filter-popover";
import { ListToolbar } from "@oba/ui/sky/list-toolbar";
import { ListEmptyState } from "@oba/ui/sky/page-states";
import { SearchInput } from "@oba/ui/sky/search-input";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import {
  useOrganizationMembers,
  useUpdateMemberRole,
  useUpdateMemberStatus,
  useRemoveMember,
} from "../hooks/use-organization-members";
import { useMemberColumns } from "../hooks/use-member-columns";
import { useMemberFilterParams } from "../hooks/use-member-filter-params";
import {
  CLEARED_MEMBER_FILTERS,
  CLEARED_MEMBER_POPOVER_FILTERS,
  filterMembers,
  hasActiveMemberFilter,
} from "../lib/member-filters";
import { DataTable } from "@/components/data-table";
import {
  useClearSearchLabel,
  useFilterPopoverLabels,
  useListEmptyLabels,
} from "@/components/list-labels";
import {
  OrganizationMemberStatusSchema,
  OrganizationRoleSchema,
  type OrganizationMember,
  type OrganizationMemberStatus,
  type OrganizationRole,
} from "@oba/shared/features/organizations/schemas";
import { CreateInvitationDialog } from "./create-organization-invitation-dialog";

interface MembersTabProps {
  organizationId: string;
  canEdit: boolean;
}

/**
 * The people of the organization, with a search on name and email and role
 * and status filters in the URL; the API answers with every member, so the
 * list is narrowed here. Every member reads and searches the list; only an
 * OWNER or ADMIN invites, edits and removes.
 */
export function MembersTab({ organizationId, canEdit }: MembersTabProps) {
  const t = useTranslations("organization");
  const [inviteOpen, setInviteOpen] = useState(false);
  const [filters, setFilters] = useMemberFilterParams();
  const filterLabels = useFilterPopoverLabels();
  const clearSearchLabel = useClearSearchLabel();
  const emptyLabels = useListEmptyLabels({
    emptyTitle: t("members.noMembers"),
    emptyDescription: canEdit ? t("members.noMembersDescription") : t("members.noMembersReadOnly"),
    filteredTitle: t("members.filteredTitle"),
  });
  const { data: meResponse } = useGetMe();

  const currentUserId = meResponse?.data?.id;
  const currentUserRole = meResponse?.data?.organizations?.find(
    (o) => o.id === organizationId
  )?.role;

  const { data: membersResponse, isLoading } = useOrganizationMembers(organizationId);
  const { mutate: updateRole, isPending: isUpdatingRole } = useUpdateMemberRole(organizationId);
  const { mutate: updateStatus, isPending: isUpdatingStatus } =
    useUpdateMemberStatus(organizationId);
  const { mutate: removeMember, isPending: isRemoving } = useRemoveMember(organizationId);

  const [editingUserId, setEditingUserId] = useState<string | null>(null);

  const members = useMemo(
    () => filterMembers(membersResponse?.data ?? [], filters),
    [membersResponse?.data, filters]
  );

  // The handlers are dependencies of the column memo. A new identity rebuilds
  // every column and remounts every cell, which closes an open row menu or
  // dialog, so they are kept stable.
  const handleRoleChange = useCallback(
    (userId: string, role: OrganizationRole) => {
      updateRole({ userId, data: { role } });
    },
    [updateRole]
  );

  const handleStatusChange = useCallback(
    (userId: string, status: OrganizationMemberStatus) => {
      updateStatus({ userId, status });
    },
    [updateStatus]
  );

  const handleRemove = useCallback(
    (userId: string) => {
      removeMember(userId);
    },
    [removeMember]
  );

  const handleEdit = useCallback((userId: string) => {
    setEditingUserId((prev) => (prev === userId ? null : userId));
  }, []);

  const columns = useMemberColumns({
    canEdit,
    currentUserId,
    currentUserRole,
    editingUserId,
    onEdit: handleEdit,
    onRoleChange: handleRoleChange,
    onStatusChange: handleStatusChange,
    onRemove: handleRemove,
    isUpdating: isUpdatingRole || isUpdatingStatus,
    isRemoving,
  });

  const filterCategories: FilterCategory[] = [
    {
      key: "role",
      label: t("members.role"),
      mode: "single",
      value: filters.role,
      onChange: (value) => {
        const role = OrganizationRoleSchema.safeParse(value);
        void setFilters({ role: role.success ? role.data : null });
      },
      options: OrganizationRoleSchema.options.map((value) => ({
        value,
        label: t(`members.roles.${value}`),
      })),
    },
    {
      key: "status",
      label: t("members.status"),
      mode: "single",
      value: filters.status,
      onChange: (value) => {
        const status = OrganizationMemberStatusSchema.safeParse(value);
        void setFilters({ status: status.success ? status.data : null });
      },
      options: OrganizationMemberStatusSchema.options.map((value) => ({
        value,
        label: t(`members.statuses.${value}`),
      })),
    },
  ];

  const inviteButton = canEdit ? (
    <Button onClick={() => setInviteOpen(true)}>
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
            label={t("members.searchLabel")}
            clearLabel={clearSearchLabel}
          />
        }
        filters={
          <FilterPopover
            categories={filterCategories}
            onClear={() => void setFilters(CLEARED_MEMBER_POPOVER_FILTERS)}
            labels={filterLabels}
          />
        }
        create={inviteButton}
      />

      <DataTable<OrganizationMember>
        columns={columns}
        data={members}
        isLoading={isLoading}
        loadingLabel={t("members.loading")}
        getRowId={(member: OrganizationMember) => member.userId}
        emptyMessage={
          <ListEmptyState
            filtered={hasActiveMemberFilter(filters)}
            onClearFilters={() => void setFilters(CLEARED_MEMBER_FILTERS)}
            create={inviteButton}
            labels={emptyLabels}
          />
        }
      />

      {canEdit && (
        <CreateInvitationDialog
          organizationId={organizationId}
          currentUserRole={currentUserRole}
          open={inviteOpen}
          onOpenChange={setInviteOpen}
        />
      )}
    </div>
  );
}
