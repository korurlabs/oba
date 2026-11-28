"use client";

import { useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import type { ColumnDef } from "@tanstack/react-table";
import { MoreVertical, PanelRight, Pencil, RefreshCw, Trash2 } from "lucide-react";
import { Button } from "@oba/ui/sky/button";
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
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import type { AzureConnection } from "../schemas";
import { AzureLastError } from "../components/azure-last-error";

interface AzureConnectionColumnsOptions {
  onOpen: (connection: AzureConnection) => void;
  onEdit: (connection: AzureConnection) => void;
  onVerify: (connectionId: string) => void;
  onDelete: (connectionId: string) => void;
  isVerifying: boolean;
  isDeleting: boolean;
}

/** Columns of the Azure connection list: name, tenant id, status as text, last check. */
export function useAzureConnectionColumns({
  onOpen,
  onEdit,
  onVerify,
  onDelete,
  isVerifying,
  isDeleting,
}: AzureConnectionColumnsOptions) {
  const t = useTranslations("integrations.azure");
  const { formatDateTime } = useDateFormat();

  return useMemo<ColumnDef<AzureConnection, unknown>[]>(
    () => [
      {
        id: "name",
        accessorKey: "name",
        header: t("columns.name"),
        cell: ({ row }) => (
          <button
            type="button"
            className="text-foreground block max-w-[220px] truncate text-left text-sm font-medium hover:underline"
            onClick={() => onOpen(row.original)}
          >
            {row.original.name}
          </button>
        ),
      },
      {
        id: "tenantId",
        accessorKey: "tenantId",
        header: t("columns.tenantId"),
        cell: ({ row }) => (
          <span className="font-mono text-xs whitespace-nowrap">{row.original.tenantId}</span>
        ),
      },
      {
        id: "status",
        accessorKey: "status",
        header: t("columns.status"),
        cell: ({ row }) => (
          <span className="text-sm whitespace-nowrap">{t(`statuses.${row.original.status}`)}</span>
        ),
      },
      {
        id: "lastVerifiedAt",
        accessorKey: "lastVerifiedAt",
        header: t("columns.lastVerifiedAt"),
        cell: ({ row }) => (
          <span className="text-sm whitespace-nowrap">
            {row.original.lastVerifiedAt
              ? formatDateTime(row.original.lastVerifiedAt)
              : t("neverVerified")}
          </span>
        ),
      },
      {
        id: "lastError",
        accessorKey: "lastError",
        header: t("columns.lastError"),
        meta: { flex: true },
        cell: ({ row }) => <AzureLastError lastError={row.original.lastError} />,
      },
      {
        id: "actions",
        header: "",
        size: 50,
        cell: ({ row }) => (
          <AzureConnectionActions
            connection={row.original}
            onOpen={onOpen}
            onEdit={onEdit}
            onVerify={onVerify}
            onDelete={onDelete}
            isVerifying={isVerifying}
            isDeleting={isDeleting}
          />
        ),
      },
    ],
    [t, formatDateTime, onOpen, onEdit, onVerify, onDelete, isVerifying, isDeleting]
  );
}

/** Row menu of a connection, with the delete confirmation beside it. */
function AzureConnectionActions({
  connection,
  onOpen,
  onEdit,
  onVerify,
  onDelete,
  isVerifying,
  isDeleting,
}: { connection: AzureConnection } & AzureConnectionColumnsOptions) {
  const t = useTranslations("integrations.azure");
  const [confirmOpen, setConfirmOpen] = useState(false);

  // The confirmation sits beside the menu, not inside it: the menu unmounts
  // its items when it closes, which would take an open dialog with it.
  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="ghost" size="icon-sm" onClick={(e) => e.stopPropagation()}>
            <MoreVertical aria-hidden="true" />
            <span className="sr-only">{t("actions.menu")}</span>
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem onSelect={() => onOpen(connection)}>
            <PanelRight aria-hidden="true" />
            {t("actions.open")}
          </DropdownMenuItem>
          <DropdownMenuItem onSelect={() => onEdit(connection)}>
            <Pencil aria-hidden="true" />
            {t("actions.edit")}
          </DropdownMenuItem>
          <DropdownMenuItem disabled={isVerifying} onSelect={() => onVerify(connection.id)}>
            <RefreshCw aria-hidden="true" />
            {t("actions.verify")}
          </DropdownMenuItem>
          <DropdownMenuSeparator />
          <DropdownMenuItem
            variant="destructive"
            disabled={isDeleting}
            onSelect={() => setConfirmOpen(true)}
          >
            <Trash2 aria-hidden="true" />
            {t("actions.delete")}
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
      <Dialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("deleteConfirmTitle")}</DialogTitle>
            <DialogDescription>
              {t("deleteConfirmDescription", { name: connection.name })}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">{t("cancel")}</Button>
            </DialogClose>
            <DialogClose asChild>
              <Button variant="destructive" onClick={() => onDelete(connection.id)}>
                {t("actions.delete")}
              </Button>
            </DialogClose>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
