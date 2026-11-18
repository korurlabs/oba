"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import type { ColumnDef } from "@tanstack/react-table";
import type { InvitationResponse } from "@oba/shared/features/users/schema";
import { InvitationActionDialog } from "../components/invitation-action-dialog";
import { OrganizationRoleBadge } from "@oba/shared/components/organization-role-badge";

interface PendingInvitationColumnsOptions {
  onAccept: (id: string) => void;
  onDecline: (id: string) => void;
  onRemindLater: (id: string) => void;
  isLoading: boolean;
}

export function usePendingInvitationColumns({
  onAccept,
  onDecline,
  onRemindLater,
  isLoading,
}: PendingInvitationColumnsOptions) {
  const t = useTranslations("pendingInvitations");
  const { formatDate } = useDateFormat();

  return useMemo<ColumnDef<InvitationResponse, unknown>[]>(
    () => [
      {
        id: "organizationName",
        accessorKey: "organizationName",
        header: t("columns.organization"),
        cell: ({ row }) => (
          <span className="font-medium">{row.original.organizationName}</span>
        ),
      },
      {
        id: "role",
        accessorKey: "role",
        header: t("columns.role"),
        cell: ({ row }) => <OrganizationRoleBadge role={row.original.role} />,
      },
      {
        id: "invitedByEmail",
        accessorKey: "invitedByEmail",
        header: t("columns.invitedBy"),
        cell: ({ row }) => (
          <span className="text-muted-foreground text-sm">
            {row.original.invitedByEmail}
          </span>
        ),
      },
      {
        id: "expiresAt",
        accessorKey: "expiresAt",
        header: t("columns.expiresAt"),
        cell: ({ row }) =>
          row.original.expiresAt
            ? formatDate(row.original.expiresAt)
            : "\u2014",
      },
      {
        id: "actions",
        header: "",
        cell: ({ row }) => (
          <div className="flex items-center gap-2">
            <InvitationActionDialog
              trigger={t("accept")}
              title={t("acceptConfirmTitle")}
              description={t("acceptConfirmDescription", {
                org: row.original.organizationName,
              })}
              confirmLabel={t("accept")}
              confirmVariant="default"
              onConfirm={() => onAccept(row.original.id)}
              disabled={isLoading}
            />
            <InvitationActionDialog
              trigger={t("decline")}
              title={t("declineConfirmTitle")}
              description={t("declineConfirmDescription", {
                org: row.original.organizationName,
              })}
              confirmLabel={t("decline")}
              confirmVariant="destructive"
              onConfirm={() => onDecline(row.original.id)}
              disabled={isLoading}
            />
            <InvitationActionDialog
              trigger={t("remindLater")}
              title={t("remindLaterConfirmTitle")}
              description={t("remindLaterConfirmDescription")}
              confirmLabel={t("remindLater")}
              confirmVariant="outline"
              onConfirm={() => onRemindLater(row.original.id)}
              disabled={isLoading}
            />
          </div>
        ),
      },
    ],
    [t, formatDate, onAccept, onDecline, onRemindLater, isLoading],
  );
}
