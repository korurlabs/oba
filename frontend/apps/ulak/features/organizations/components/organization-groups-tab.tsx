"use client";

import { useCallback, useState } from "react";
import { useTranslations } from "next-intl";
import { Plus } from "lucide-react";
import { Button } from "@oba/ui/sky/button";
import { ListToolbar } from "@oba/ui/sky/list-toolbar";
import { EmptyState } from "@oba/ui/sky/page-states";
import { DataTable } from "@/components/data-table";
import type { OrganizationGroup } from "@oba/shared/features/organizations/schemas";
import {
  useDeleteOrganizationGroup,
  useOrganizationGroups,
} from "../hooks/use-organization-groups";
import { useGroupColumns } from "../hooks/use-group-columns";
import { GroupFormDialog } from "./group-form-dialog";
import { GroupMembersSheet } from "./group-members-sheet";
import { OrganizationLoadError } from "./organization-load-error";

interface GroupsTabProps {
  organizationId: string;
}

/**
 * Groups of the organization, "Everyone" first. Shown only to an OWNER or
 * ADMIN, who are the only roles that can read groups, so every control is
 * editable here.
 */
export function GroupsTab({ organizationId }: GroupsTabProps) {
  const t = useTranslations("organization");

  const {
    data: groupsResponse,
    isLoading,
    isError,
    refetch,
  } = useOrganizationGroups(organizationId);
  const [formOpen, setFormOpen] = useState(false);
  const [renamingGroup, setRenamingGroup] = useState<OrganizationGroup | null>(null);
  const [membersOpen, setMembersOpen] = useState(false);
  const [membersGroup, setMembersGroup] = useState<OrganizationGroup | null>(null);

  // The members sheet keeps its group after it closes, so its member query
  // stays enabled. Letting go of a deleted group disables that query before
  // the group list refetch re-renders the sheet.
  const { mutate: deleteGroup, isPending: isDeleting } = useDeleteOrganizationGroup(
    organizationId,
    {
      onBeforeInvalidate: (deletedGroupId) => {
        if (membersGroup?.id !== deletedGroupId) return;
        setMembersOpen(false);
        setMembersGroup(null);
      },
    }
  );

  const groups = groupsResponse?.data ?? [];

  // The handlers are dependencies of the column memo. A new identity rebuilds
  // every column and remounts every cell, which closes an open row menu or
  // dialog, so they are kept stable.
  const handleShowMembers = useCallback((group: OrganizationGroup) => {
    setMembersGroup(group);
    setMembersOpen(true);
  }, []);

  const handleRename = useCallback((group: OrganizationGroup) => {
    setRenamingGroup(group);
    setFormOpen(true);
  }, []);

  const handleDelete = useCallback(
    (groupId: string) => {
      deleteGroup(groupId);
    },
    [deleteGroup]
  );

  const handleCreate = () => {
    setRenamingGroup(null);
    setFormOpen(true);
  };

  const columns = useGroupColumns({
    onShowMembers: handleShowMembers,
    onRename: handleRename,
    onDelete: handleDelete,
    isDeleting,
  });

  return (
    <div className="flex flex-col gap-4">
      <ListToolbar
        create={
          <Button size="sm" onClick={handleCreate}>
            <Plus aria-hidden="true" />
            {t("groups.newGroup")}
          </Button>
        }
      />

      {isError && !groupsResponse ? (
        <OrganizationLoadError onRetry={() => void refetch()} />
      ) : (
        <DataTable<OrganizationGroup>
          columns={columns}
          data={groups}
          isLoading={isLoading}
          loadingLabel={t("groups.loading")}
          getRowId={(group: OrganizationGroup) => group.id}
          emptyMessage={
            <EmptyState
              title={t("groups.noGroups")}
              description={t("groups.noGroupsDescription")}
            />
          }
        />
      )}

      <GroupFormDialog
        organizationId={organizationId}
        group={renamingGroup}
        open={formOpen}
        onOpenChange={setFormOpen}
      />

      <GroupMembersSheet
        organizationId={organizationId}
        group={membersGroup}
        open={membersOpen}
        onOpenChange={setMembersOpen}
      />
    </div>
  );
}
