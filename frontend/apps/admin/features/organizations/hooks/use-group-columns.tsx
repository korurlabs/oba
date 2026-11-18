"use client";

import { useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { Badge } from "@oba/ui/sky/badge";
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
import { MoreVertical, Pencil, Trash2, Users } from "lucide-react";
import type { ColumnDef } from "@tanstack/react-table";
import type { OrganizationGroup } from "@oba/shared/features/organizations/schemas";
import { AppBadges, useAppName } from "../components/app-badges";

interface GroupColumnsOptions {
  onShowMembers: (group: OrganizationGroup) => void;
  onRename: (group: OrganizationGroup) => void;
  onDelete: (groupId: string) => void;
  isDeleting: boolean;
}

export function useGroupColumns({
  onShowMembers,
  onRename,
  onDelete,
  isDeleting,
}: GroupColumnsOptions) {
  const t = useTranslations("organization");

  return useMemo<ColumnDef<OrganizationGroup, unknown>[]>(
    () => [
      {
        id: "name",
        accessorKey: "name",
        header: t("groups.name"),
        cell: ({ row }) => (
          <div className="flex items-center gap-2">
            <span className="text-foreground block max-w-[200px] truncate text-sm font-medium">
              {row.original.name}
            </span>
            {row.original.type === "EVERYONE" && (
              <Badge variant="outline">{t("groups.system")}</Badge>
            )}
          </div>
        ),
      },
      {
        id: "description",
        accessorKey: "description",
        header: t("groups.description"),
        meta: { flex: true },
        cell: ({ row }) => (
          <span className="text-muted-foreground block max-w-[260px] truncate text-sm">
            {row.original.description ?? ""}
          </span>
        ),
      },
      {
        id: "memberCount",
        accessorKey: "memberCount",
        header: t("groups.members"),
        cell: ({ row }) => (
          <span className="text-foreground text-sm tabular-nums">{row.original.memberCount}</span>
        ),
      },
      {
        id: "apps",
        accessorKey: "apps",
        header: t("groups.apps"),
        cell: ({ row }) => <AppBadges apps={row.original.apps} emptyLabel={t("groups.noApps")} />,
      },
      {
        id: "actions",
        header: "",
        size: 50,
        cell: ({ row }) => (
          <GroupActions
            group={row.original}
            onShowMembers={onShowMembers}
            onRename={onRename}
            onDelete={onDelete}
            isDeleting={isDeleting}
          />
        ),
      },
    ],
    [t, onShowMembers, onRename, onDelete, isDeleting]
  );
}

/**
 * Row menu of a group. "Everyone" is the system group of every member: it
 * cannot be renamed or deleted, so its menu offers only the member list.
 */
function GroupActions({
  group,
  onShowMembers,
  onRename,
  onDelete,
  isDeleting,
}: {
  group: OrganizationGroup;
  onShowMembers: (group: OrganizationGroup) => void;
  onRename: (group: OrganizationGroup) => void;
  onDelete: (groupId: string) => void;
  isDeleting: boolean;
}) {
  const t = useTranslations("organization");
  const appName = useAppName();
  const [confirmOpen, setConfirmOpen] = useState(false);
  const isSystemGroup = group.type === "EVERYONE";

  // The confirmation sits beside the menu, not inside it: the menu unmounts
  // its items when it closes, which would take an open dialog with it.
  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="ghost" size="icon-sm" onClick={(e) => e.stopPropagation()}>
            <MoreVertical aria-hidden="true" />
            <span className="sr-only">{t("groups.actions")}</span>
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem onSelect={() => onShowMembers(group)}>
            <Users aria-hidden="true" />
            {t("groups.viewMembers")}
          </DropdownMenuItem>
          {!isSystemGroup && (
            <>
              <DropdownMenuItem onSelect={() => onRename(group)}>
                <Pencil aria-hidden="true" />
                {t("groups.rename")}
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem
                variant="destructive"
                disabled={isDeleting}
                onSelect={() => setConfirmOpen(true)}
              >
                <Trash2 aria-hidden="true" />
                {t("groups.delete")}
              </DropdownMenuItem>
            </>
          )}
        </DropdownMenuContent>
      </DropdownMenu>
      {!isSystemGroup && (
        <Dialog open={confirmOpen} onOpenChange={setConfirmOpen}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>{t("groups.deleteConfirmTitle")}</DialogTitle>
              <DialogDescription>
                {t("groups.deleteConfirmDescription", { name: group.name })}{" "}
                {group.apps.length > 0
                  ? t("groups.deleteConfirmGrants", {
                      apps: group.apps.map(appName).join(", "),
                    })
                  : t("groups.deleteConfirmNoGrants")}
              </DialogDescription>
            </DialogHeader>
            <DialogFooter>
              <DialogClose asChild>
                <Button variant="outline">{t("groups.cancel")}</Button>
              </DialogClose>
              <DialogClose asChild>
                <Button variant="destructive" onClick={() => onDelete(group.id)}>
                  {t("groups.delete")}
                </Button>
              </DialogClose>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      )}
    </>
  );
}
