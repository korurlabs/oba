"use client";

import { useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { Badge } from "@oba/ui/sky/badge";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@oba/ui/sky/dropdown-menu";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@oba/ui/sky/dialog";
import { Button } from "@oba/ui/sky/button";
import { MoreVertical, Pencil, Trash2 } from "lucide-react";
import type { ColumnDef } from "@tanstack/react-table";
import type { OrganizationMember, OrganizationMemberStatus, OrganizationRole } from "@oba/shared/features/organizations/schemas";
import { ROLE_OPTIONS, STATUS_OPTIONS } from "@oba/shared/features/organizations/lib/organization-options";
import { OrganizationRoleBadge } from "@oba/shared/components/organization-role-badge";
import { OrganizationMemberStatusBadge } from "../components/organization-member-status-badge";

interface MemberColumnsOptions {
  canEdit: boolean;
  currentUserId: string | undefined;
  currentUserRole: OrganizationRole | undefined;
  editingUserId: string | null;
  onEdit: (userId: string) => void;
  onRoleChange: (userId: string, role: OrganizationRole) => void;
  onStatusChange: (userId: string, status: OrganizationMemberStatus) => void;
  onRemove: (userId: string) => void;
  isUpdating: boolean;
  isRemoving: boolean;
}

export function useMemberColumns({
  canEdit,
  currentUserId,
  currentUserRole,
  editingUserId,
  onEdit,
  onRoleChange,
  onStatusChange,
  onRemove,
  isUpdating,
  isRemoving,
}: MemberColumnsOptions) {
  const t = useTranslations("organization");

  return useMemo<ColumnDef<OrganizationMember, unknown>[]>(
    () => [
      {
        id: "userId",
        accessorKey: "firstName",
        header: t("members.userId"),
        cell: ({ row }) => (
          <span className="font-mono text-sm text-foreground truncate block max-w-[200px]">
            {row.original.firstName} {row.original.lastName}
          </span>
        ),
      },
      {
        id: "email",
        accessorKey: "email",
        header: t("members.email"),
        cell: ({ row }) => (
          <span className="font-mono text-sm text-foreground truncate block max-w-[200px]">
            {row.original.email}
          </span>
        ),
      },
      {
        id: "role",
        accessorKey: "role",
        header: t("members.role"),
        cell: ({ row }) => {
          const isEditing = editingUserId === row.original.userId;

          const availableRoles =
            currentUserRole === "OWNER"
              ? ROLE_OPTIONS
              : ROLE_OPTIONS.filter((opt) => opt.value !== "OWNER");

          const isRoleEditable = isEditing && availableRoles.some((opt) => opt.value === row.original.role);

          if (!isRoleEditable) {
            return <OrganizationRoleBadge role={row.original.role} />;
          }

          return (
            <Select
              value={row.original.role}
              onValueChange={(value) =>
                onRoleChange(row.original.userId, value as OrganizationRole)
              }
              disabled={isUpdating}
            >
              <SelectTrigger size="md" className="w-fit gap-2">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {availableRoles.map(({ value, icon: Icon }) => (
                  <SelectItem key={value} value={value}>
                    <div className="flex items-center gap-2">
                      <Icon className="h-4 w-4" />
                      <span>{t(`members.roles.${value}`)}</span>
                    </div>
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          );
        },
      },
      {
        id: "status",
        accessorKey: "status",
        header: t("members.status"),
        cell: ({ row }) => {
          if (editingUserId !== row.original.userId) {
            return <OrganizationMemberStatusBadge status={row.original.status} />;
          }

          return (
            <Select
              value={row.original.status}
              onValueChange={(value) =>
                onStatusChange(row.original.userId, value as OrganizationMemberStatus)
              }
              disabled={isUpdating}
            >
              <SelectTrigger size="md" className="w-fit gap-2">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {STATUS_OPTIONS.map(({ value, icon: Icon }) => (
                  <SelectItem key={value} value={value}>
                    <Badge
                      variant={value === "DISABLED" ? "error" : "success"}
                      className="gap-1.5 whitespace-nowrap pointer-events-none"
                    >
                      <Icon className="h-3 w-3" />
                      {t(`members.statuses.${value}`)}
                    </Badge>
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          );
        },
      },
      ...(canEdit
        ? [
            {
              id: "actions",
              header: "",
              size: 50,
              cell: ({ row }) => {
                if (row.original.userId === currentUserId) {
                  return null;
                }

                if (row.original.role === "OWNER" && currentUserRole !== "OWNER") {
                  return null;
                }

                return (
                  <MemberActions
                    member={row.original}
                    isEditing={editingUserId === row.original.userId}
                    onEdit={onEdit}
                    onRemove={onRemove}
                    isRemoving={isRemoving}
                    t={t}
                  />
                );
              },
            } satisfies ColumnDef<OrganizationMember, unknown>,
          ]
        : []),
    ],
    [t, canEdit, currentUserId, currentUserRole, editingUserId, onEdit, onRoleChange, onStatusChange, onRemove, isUpdating, isRemoving],
  );
}

function MemberActions({
  member,
  isEditing,
  onEdit,
  onRemove,
  isRemoving,
  t,
}: {
  member: OrganizationMember;
  isEditing: boolean;
  onEdit: (userId: string) => void;
  onRemove: (userId: string) => void;
  isRemoving: boolean;
  t: ReturnType<typeof useTranslations>;
}) {
  const [confirmOpen, setConfirmOpen] = useState(false);

  // The confirmation sits beside the menu, not inside it: the menu unmounts
  // its items when it closes, which would take an open dialog with it.
  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button
            variant="ghost"
            size="icon-sm"
            onClick={(e) => e.stopPropagation()}
          >
            <MoreVertical aria-hidden="true" />
            <span className="sr-only">{t("members.actions")}</span>
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem onSelect={() => onEdit(member.userId)}>
            <Pencil aria-hidden="true" />
            {isEditing ? t("members.cancelEdit") : t("members.edit")}
          </DropdownMenuItem>
          <DropdownMenuSeparator />
          <DropdownMenuItem
            variant="destructive"
            disabled={isRemoving}
            onSelect={() => setConfirmOpen(true)}
          >
            <Trash2 aria-hidden="true" />
            {t("members.remove")}
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
      <Dialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("members.removeConfirmTitle")}</DialogTitle>
            <DialogDescription>{t("members.removeConfirmDescription")}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">{t("members.cancel")}</Button>
            </DialogClose>
            <DialogClose asChild>
              <Button variant="destructive" onClick={() => onRemove(member.userId)}>
                {t("members.remove")}
              </Button>
            </DialogClose>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
