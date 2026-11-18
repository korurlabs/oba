"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import { Pencil, Trash2 } from "lucide-react";
import type { ColumnDef } from "@tanstack/react-table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { AlertRouteBoardLabel } from "../components/alert-route-board-label";
import type { AlertRoute } from "../schemas";

interface AlertRouteColumnsOptions {
  canManage: boolean;
  onEdit: (route: AlertRoute) => void;
  onRemove: (routeId: string) => void;
  isRemoving: boolean;
}

export function useAlertRouteColumns({
  canManage,
  onEdit,
  onRemove,
  isRemoving,
}: AlertRouteColumnsOptions) {
  const t = useTranslations("alertRoutes");
  const tCommon = useTranslations("common");

  return useMemo<ColumnDef<AlertRoute, unknown>[]>(
    () => [
      {
        id: "systemKey",
        accessorKey: "systemKey",
        header: t("columns.system"),
        cell: ({ row }) => (
          <span className="font-mono text-sm">{row.original.systemKey}</span>
        ),
      },
      {
        id: "board",
        accessorKey: "boardName",
        header: t("columns.board"),
        cell: ({ row }) => (
          <AlertRouteBoardLabel
            name={row.original.boardName}
            prefix={row.original.boardPrefix}
            color={row.original.boardColor}
            boardId={row.original.boardId}
          />
        ),
      },
      {
        id: "isDefault",
        accessorKey: "isDefault",
        header: t("columns.default"),
        cell: ({ row }) =>
          row.original.isDefault ? (
            <Badge variant="default" className="whitespace-nowrap">
              {t("columns.default")}
            </Badge>
          ) : null,
      },
      ...(canManage
        ? [
            {
              id: "actions",
              header: "",
              cell: ({ row }) => (
                <div className="flex items-center justify-end gap-1">
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    aria-label={t("edit")}
                    title={t("edit")}
                    onClick={() => onEdit(row.original)}
                  >
                    <Pencil className="h-4 w-4" />
                  </Button>
                  <AlertDialog>
                    <AlertDialogTrigger asChild>
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        aria-label={t("remove")}
                        title={t("remove")}
                        className="text-destructive hover:text-destructive hover:[&_svg]:text-destructive"
                        disabled={isRemoving}
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </AlertDialogTrigger>
                    <AlertDialogContent>
                      <AlertDialogHeader>
                        <AlertDialogTitle>{t("removeTitle")}</AlertDialogTitle>
                        <AlertDialogDescription>
                          {t("removeConfirm", { system: row.original.systemKey })}
                        </AlertDialogDescription>
                      </AlertDialogHeader>
                      <AlertDialogFooter>
                        <AlertDialogCancel>{tCommon("cancel")}</AlertDialogCancel>
                        <AlertDialogAction
                          variant="destructive"
                          onClick={() => onRemove(row.original.id)}
                        >
                          {t("remove")}
                        </AlertDialogAction>
                      </AlertDialogFooter>
                    </AlertDialogContent>
                  </AlertDialog>
                </div>
              ),
            } satisfies ColumnDef<AlertRoute, unknown>,
          ]
        : []),
    ],
    [t, tCommon, canManage, onEdit, onRemove, isRemoving],
  );
}
