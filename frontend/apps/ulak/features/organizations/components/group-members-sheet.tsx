"use client";

import { useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { Trash2, UserPlus } from "lucide-react";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@oba/ui/sky/sheet";
import { Button } from "@oba/ui/sky/button";
import { TableLoading } from "@oba/ui/sky/page-states";
import { SelectFieldStandalone, type SelectOption } from "@oba/shared/features/form";
import { OrganizationRoleBadge } from "@oba/shared/components/organization-role-badge";
import type {
  OrganizationGroup,
  OrganizationGroupMember,
} from "@oba/shared/features/organizations/schemas";
import {
  useAddOrganizationGroupMembers,
  useOrganizationGroupMembers,
  useRemoveOrganizationGroupMember,
} from "../hooks/use-organization-groups";
import { useOrganizationMembers } from "../hooks/use-organization-members";
import { OrganizationLoadError } from "./organization-load-error";

/** The API takes at most this many user ids in one add. */
const MAX_MEMBERS_PER_ADD = 100;

interface GroupMembersSheetProps {
  organizationId: string;
  /**
   * The group whose members are shown. It is kept after the sheet closes, so
   * the title does not go blank during the close animation.
   */
  group: OrganizationGroup | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * Members of one group. The members of "Everyone" are every active member of
 * the organization and are derived by the API, so that list is read-only.
 */
export function GroupMembersSheet({
  organizationId,
  group,
  open,
  onOpenChange,
}: GroupMembersSheetProps) {
  const t = useTranslations("organization");
  const isSystemGroup = group?.type === "EVERYONE";

  const {
    data: groupMembersResponse,
    isLoading,
    isError,
    refetch,
  } = useOrganizationGroupMembers(organizationId, group?.id);
  const groupMembers = groupMembersResponse?.data ?? [];

  return (
    <Sheet open={open && group !== null} onOpenChange={onOpenChange}>
      <SheetContent className="flex w-full flex-col gap-0 sm:max-w-md">
        <SheetHeader>
          <SheetTitle>{t("groups.membersTitle", { name: group?.name ?? "" })}</SheetTitle>
          <SheetDescription>
            {isSystemGroup ? t("groups.everyoneMembers") : t("groups.membersDescription")}
          </SheetDescription>
        </SheetHeader>

        <div className="flex flex-1 flex-col gap-4 overflow-y-auto px-4 pb-4">
          {group && !isSystemGroup && (
            <AddGroupMembers
              organizationId={organizationId}
              groupId={group.id}
              currentMembers={groupMembers}
              disabled={isLoading || isError}
            />
          )}

          {isLoading ? (
            <TableLoading label={t("groups.membersLoading")} />
          ) : isError ? (
            <OrganizationLoadError onRetry={() => void refetch()} />
          ) : groupMembers.length === 0 ? (
            <p className="text-muted-foreground py-6 text-center text-sm">
              {t("groups.noGroupMembers")}
            </p>
          ) : (
            <GroupMemberList
              organizationId={organizationId}
              groupId={group?.id}
              members={groupMembers}
              canRemove={!isSystemGroup}
            />
          )}
        </div>
      </SheetContent>
    </Sheet>
  );
}

function GroupMemberList({
  organizationId,
  groupId,
  members,
  canRemove,
}: {
  organizationId: string;
  groupId: string | undefined;
  members: OrganizationGroupMember[];
  canRemove: boolean;
}) {
  const t = useTranslations("organization");
  const removeMember = useRemoveOrganizationGroupMember(organizationId);
  const removingUserId = removeMember.isPending ? removeMember.variables?.userId : undefined;

  return (
    <ul className="divide-border divide-y rounded-lg border">
      {members.map((member) => (
        <li key={member.userId} className="flex items-center gap-3 px-3 py-2">
          <div className="min-w-0 flex-1">
            <p className="text-foreground truncate text-sm font-medium">{member.fullName}</p>
            <p className="text-muted-foreground truncate text-xs">{member.email}</p>
          </div>
          {member.role && <OrganizationRoleBadge role={member.role} />}
          {canRemove && groupId && (
            <Button
              variant="ghost"
              size="icon-sm"
              disabled={removeMember.isPending}
              aria-busy={removingUserId === member.userId}
              onClick={() => removeMember.mutate({ groupId, userId: member.userId })}
            >
              <Trash2 aria-hidden="true" />
              <span className="sr-only">{t("groups.removeMember", { name: member.fullName })}</span>
            </Button>
          )}
        </li>
      ))}
    </ul>
  );
}

/**
 * Picks organization members who are not in the group yet and adds them in
 * one request. Only active members are offered: a disabled member cannot use
 * any app, so a grant through the group would change nothing.
 */
function AddGroupMembers({
  organizationId,
  groupId,
  currentMembers,
  disabled,
}: {
  organizationId: string;
  groupId: string;
  currentMembers: OrganizationGroupMember[];
  disabled: boolean;
}) {
  const t = useTranslations("organization");
  const [selectedUserIds, setSelectedUserIds] = useState<string[]>([]);
  const {
    data: membersResponse,
    isLoading: isMembersLoading,
    isError: isMembersError,
  } = useOrganizationMembers(organizationId);
  const addMembers = useAddOrganizationGroupMembers(organizationId);

  const options = useMemo<SelectOption[]>(() => {
    const currentIds = new Set(currentMembers.map((member) => member.userId));
    const limitReached = selectedUserIds.length >= MAX_MEMBERS_PER_ADD;
    return (membersResponse?.data ?? [])
      .filter((member) => member.status === "ACTIVE" && !currentIds.has(member.userId))
      .map((member) => ({
        value: member.userId,
        label: `${member.firstName} ${member.lastName} (${member.email})`,
        disabled: limitReached && !selectedUserIds.includes(member.userId),
        disabledLabel: limitReached ? t("groups.addMembersLimit") : undefined,
      }));
  }, [membersResponse, currentMembers, selectedUserIds, t]);

  // A failed add is shown by the toast of useAddOrganizationGroupMembers and
  // keeps the selection, so the user can try again.
  const handleAdd = () => {
    addMembers.mutate(
      { groupId, data: { userIds: selectedUserIds } },
      { onSuccess: () => setSelectedUserIds([]) }
    );
  };

  return (
    <div className="flex flex-col gap-2 pt-1">
      <SelectFieldStandalone
        isMulti
        label={t("groups.addMembers")}
        placeholder={
          isMembersError
            ? t("groups.addMembersLoadError")
            : options.length === 0 && !isMembersLoading
              ? t("groups.noMembersToAdd")
              : t("groups.addMembersPlaceholder")
        }
        searchPlaceholder={t("groups.searchMembers")}
        options={options}
        value={selectedUserIds}
        onValueChange={setSelectedUserIds}
        isLoading={isMembersLoading}
        disabled={disabled || isMembersError || addMembers.isPending}
      />
      <Button
        size="sm"
        className="self-end"
        disabled={selectedUserIds.length === 0 || addMembers.isPending}
        onClick={handleAdd}
      >
        <UserPlus aria-hidden="true" />
        {addMembers.isPending
          ? t("groups.adding")
          : t("groups.addSelected", { count: selectedUserIds.length })}
      </Button>
    </div>
  );
}
