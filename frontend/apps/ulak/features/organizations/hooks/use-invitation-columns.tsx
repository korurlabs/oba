"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { Badge } from "@oba/ui/sky/badge";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@oba/ui/sky/dialog";
import { Button } from "@oba/ui/sky/button";
import { X } from "lucide-react";
import type { ColumnDef } from "@tanstack/react-table";
import type { Invitation } from "@oba/shared/features/organizations/schemas";
import { OrganizationRoleBadge } from "@oba/shared/components/organization-role-badge";

const STATUS_VARIANT_MAP: Record<
  Invitation["status"],
  "info" | "success" | "muted" | "error"
> = {
  PENDING: "info",
  ACCEPTED: "success",
  EXPIRED: "muted",
  DECLINED: "error",
  REVOKED: "muted",
};

interface InvitationColumnsOptions {
  canEdit: boolean;
  onRevoke: (invitationId: string) => void;
  isRevoking: boolean;
}

export function useInvitationColumns({
  canEdit,
  onRevoke,
  isRevoking,
}: InvitationColumnsOptions) {
  const t = useTranslations("organization");
  const { formatDate } = useDateFormat();

  return useMemo<ColumnDef<Invitation, unknown>[]>(
    () => [
      {
        id: "email",
        accessorKey: "email",
        header: t("invitations.email"),
        cell: ({ row }) => (
          <span className="font-medium">{row.original.email}</span>
        ),
      },
      {
        id: "role",
        accessorKey: "role",
        header: t("invitations.role"),
        cell: ({ row }) => <OrganizationRoleBadge role={row.original.role} />,
      },
      {
        id: "status",
        accessorKey: "status",
        header: t("invitations.status"),
        cell: ({ row }) => (
          <Badge
            variant={STATUS_VARIANT_MAP[row.original.status]}
            className="whitespace-nowrap"
          >
            {t(`invitations.statuses.${row.original.status}`)}
          </Badge>
        ),
      },
      {
        id: "invitedByEmail",
        accessorKey: "invitedByEmail",
        header: t("invitations.invitedBy"),
        cell: ({ row }) => (
          <span className="text-sm text-muted-foreground">
            {row.original.invitedByEmail}
          </span>
        ),
      },
      {
        id: "createdAt",
        accessorKey: "createdAt",
        header: t("invitations.createdAt"),
        cell: ({ row }) =>
          formatDate(row.original.createdAt),
      },
      ...(canEdit
        ? [
          {
            id: "actions",
            header: "",
            cell: ({ row }) => {
              if (row.original.status !== "PENDING") return null;
              return (
                <Dialog>
                  <DialogTrigger asChild>
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      className="text-destructive hover:text-destructive"
                      disabled={isRevoking}
                    >
                      <X aria-hidden="true" />
                      <span className="sr-only">{t("invitations.revoke")}</span>
                    </Button>
                  </DialogTrigger>
                  <DialogContent>
                    <DialogHeader>
                      <DialogTitle>{t("invitations.revokeConfirmTitle")}</DialogTitle>
                      <DialogDescription>
                        {t("invitations.revokeConfirmDescription")}
                      </DialogDescription>
                    </DialogHeader>
                    <DialogFooter>
                      <DialogClose asChild>
                        <Button variant="outline">{t("invitations.cancel")}</Button>
                      </DialogClose>
                      <DialogClose asChild>
                        <Button variant="destructive" onClick={() => onRevoke(row.original.id)}>
                          {t("invitations.revoke")}
                        </Button>
                      </DialogClose>
                    </DialogFooter>
                  </DialogContent>
                </Dialog>
              );
            },
          } satisfies ColumnDef<Invitation, unknown>,
        ]
        : []),
    ],
    [t, formatDate, canEdit, onRevoke, isRevoking],
  );
}
