"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import { Pencil, Trash2 } from "lucide-react";
import type { ColumnDef } from "@tanstack/react-table";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { CheckOutcomeSelect, CheckStatusSelect } from "../components/check-row-selects";
import {
  CheckOutcome as CheckOutcomeValue,
  type Check,
  type CheckOutcome,
  type CheckStatus,
} from "../schemas";

/**
 * A failed check is the one value a reader must not miss, so its chip takes
 * the red outline Kazan gives a bug; every other outcome and status stays a
 * calm neutral chip.
 */
const FAIL_CHIP_CLASS = "border-red-600/40 text-red-700 dark:border-red-400/50 dark:text-red-400";

interface CheckColumnsOptions {
  canManage: boolean;
  onEdit: (check: Check) => void;
  onDelete: (check: Check) => void;
  onOutcomeChange: (check: Check, outcome: CheckOutcome) => void;
  onStatusChange: (check: Check, status: CheckStatus) => void;
  /** The check whose outcome or status is being saved; its selects are disabled until the answer. */
  savingCheckId: string | null;
}

/**
 * Columns of the check list. Outcome and status are selects for an
 * organization admin and read-only chips for every other reader, who may not
 * change them. Figures and dates are set in mono, as on Kazan's other lists.
 */
export function useCheckColumns({
  canManage,
  onEdit,
  onDelete,
  onOutcomeChange,
  onStatusChange,
  savingCheckId,
}: CheckColumnsOptions) {
  const t = useTranslations("cyberscore");
  const dates = useDateFormat();

  return useMemo<ColumnDef<Check, unknown>[]>(
    () => [
      {
        id: "name",
        accessorKey: "name",
        header: t("checks.columns.name"),
        meta: { flex: true },
        cell: ({ row }) => (
          <div className="flex min-w-0 flex-col gap-0.5">
            <span className="text-foreground truncate text-sm font-medium">
              {row.original.name}
            </span>
            <span className="text-muted-foreground line-clamp-1 text-xs">
              {row.original.expected}
            </span>
          </div>
        ),
      },
      {
        id: "category",
        accessorKey: "category",
        header: t("checks.columns.category"),
        cell: ({ row }) => (
          <span className="text-muted-foreground text-sm whitespace-nowrap">
            {t(`categories.${row.original.category ?? "NONE"}`)}
          </span>
        ),
      },
      {
        id: "weight",
        accessorKey: "weight",
        header: t("checks.columns.weight"),
        cell: ({ row }) => (
          <span className="kazan-grid-number h-5 w-auto min-w-5 px-1">{row.original.weight}</span>
        ),
      },
      {
        id: "outcome",
        accessorKey: "outcome",
        header: t("checks.columns.outcome"),
        cell: ({ row }) =>
          canManage ? (
            <CheckOutcomeSelect
              value={row.original.outcome}
              checkName={row.original.name}
              disabled={savingCheckId === row.original.id}
              onChange={(outcome) => onOutcomeChange(row.original, outcome)}
            />
          ) : (
            <span
              className={cn(
                "kazan-grid-chip",
                row.original.outcome === CheckOutcomeValue.FAIL && FAIL_CHIP_CLASS
              )}
            >
              {t(`outcomes.${row.original.outcome}`)}
            </span>
          ),
      },
      {
        id: "status",
        accessorKey: "status",
        header: t("checks.columns.status"),
        cell: ({ row }) =>
          canManage ? (
            <CheckStatusSelect
              value={row.original.status}
              checkName={row.original.name}
              disabled={savingCheckId === row.original.id}
              onChange={(status) => onStatusChange(row.original, status)}
            />
          ) : (
            <span className="kazan-grid-chip">{t(`statuses.${row.original.status}`)}</span>
          ),
      },
      {
        id: "updatedAt",
        accessorKey: "updatedAt",
        header: t("checks.columns.updatedAt"),
        cell: ({ row }) => (
          <span className="text-muted-foreground font-mono text-xs whitespace-nowrap tabular-nums">
            {dates.formatDate(row.original.updatedAt)}
          </span>
        ),
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
                    aria-label={t("checks.edit")}
                    title={t("checks.edit")}
                    onClick={() => onEdit(row.original)}
                  >
                    <Pencil className="h-4 w-4" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    aria-label={t("checks.delete")}
                    title={t("checks.delete")}
                    className="text-destructive hover:text-destructive hover:[&_svg]:text-destructive"
                    onClick={() => onDelete(row.original)}
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </div>
              ),
            } satisfies ColumnDef<Check, unknown>,
          ]
        : []),
    ],
    [t, dates, canManage, onEdit, onDelete, onOutcomeChange, onStatusChange, savingCheckId]
  );
}
