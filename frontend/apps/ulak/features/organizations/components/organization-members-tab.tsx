"use client";

import { useCallback, useState } from "react";
import { useTranslations } from "next-intl";
import { EmptyState } from "@oba/ui/sky/page-states";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import {
  useOrganizationMembers,
  useUpdateMemberRole,
  useUpdateMemberStatus,
  useRemoveMember,
} from "../hooks/use-organization-members";
import { useMemberColumns } from "../hooks/use-member-columns";
import { DataTable } from "@/components/data-table";
import type {
  OrganizationMember,
  OrganizationMemberStatus,
  OrganizationRole,
} from "@oba/shared/features/organizations/schemas";

interface MembersTabProps {
  organizationId: string;
  canEdit: boolean;
}

export function MembersTab({ organizationId, canEdit }: MembersTabProps) {
  const t = useTranslations("organization");
  const { data: meResponse } = useGetMe();

  const currentUserId = meResponse?.data?.id;
  const currentUserRole = meResponse?.data?.organizations?.find(
    (o) => o.id === organizationId,
  )?.role;

  const { data: membersResponse, isLoading } =
    useOrganizationMembers(organizationId);
  const { mutate: updateRole, isPending: isUpdatingRole } =
    useUpdateMemberRole(organizationId);
  const { mutate: updateStatus, isPending: isUpdatingStatus } =
    useUpdateMemberStatus(organizationId);
  const { mutate: removeMember, isPending: isRemoving } =
    useRemoveMember(organizationId);

  const [editingUserId, setEditingUserId] = useState<string | null>(null);

  const members = membersResponse?.data ?? [];

  // The handlers are dependencies of the column memo. A new identity rebuilds
  // every column and remounts every cell, which closes an open row menu or
  // dialog, so they are kept stable.
  const handleRoleChange = useCallback(
    (userId: string, role: OrganizationRole) => {
      updateRole({ userId, data: { role } });
    },
    [updateRole],
  );

  const handleStatusChange = useCallback(
    (userId: string, status: OrganizationMemberStatus) => {
      updateStatus({ userId, status });
    },
    [updateStatus],
  );

  const handleRemove = useCallback(
    (userId: string) => {
      removeMember(userId);
    },
    [removeMember],
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

  return (
    <section className="flex flex-col gap-4" aria-labelledby="organization-members-title">
      <h2 id="organization-members-title" className="text-xl">
        {t("members.title")}
      </h2>

      <DataTable<OrganizationMember>
        columns={columns}
        data={members}
        isLoading={isLoading}
        loadingLabel={t("members.loading")}
        getRowId={(member: OrganizationMember) => member.userId}
        emptyMessage={
          <EmptyState
            title={t("members.noMembers")}
            description={t("members.noMembersDescription")}
          />
        }
      />
    </section>
  );
}
