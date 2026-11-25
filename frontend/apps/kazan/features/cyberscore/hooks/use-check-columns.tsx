"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import { FileText, Pencil, Trash2 } from "lucide-react";
import type { ColumnDef } from "@tanstack/react-table";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { CheckOutcomeChip } from "../components/check-outcome-chip";
import { CheckOutcomeSelect, CheckStatusSelect } from "../components/check-row-selects";
import { splitCheckName } from "../lib/check-name";
import type { Check, CheckOutcome, CheckStatus } from "../schemas";

/** A check row, or a category heading row that holds the checks of its category. */
export type CheckTableRow = Check & { groupChecks?: Check[] };

interface CheckColumnsOptions {
  canManage: boolean;
  onEdit: (check: Check) => void;
  onDelete: (check: Check) => void;
  onOutcomeChange: (check: Check, outcome: CheckOutcome) => void;
  onStatusChange: (check: Check, status: CheckStatus) => void;
  /** The checks whose outcome or status is being saved; their selects wait for each answer. */
  savingCheckIds: ReadonlySet<string>;
}

/**
 * The full description and expected state of a check, behind a button on its
 * row. The row shows both cut to one line so the list stays scannable; the
 * popover opens on click, Enter or Space, so the full text is reachable by
 * keyboard and on a touch screen, where a hover tooltip is not.
 */
function CheckTextPopover({ check }: { check: Check }) {
  const t = useTranslations("cyberscore");
  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button
          variant="ghost"
          size="icon-sm"
          aria-label={`${t("checks.showText")}: ${check.name}`}
          title={t("checks.showText")}
          className="shrink-0"
        >
          <FileText className="h-4 w-4" />
        </Button>
      </PopoverTrigger>
      <PopoverContent
        align="start"
        className="flex max-h-[min(28rem,70vh)] w-[min(36rem,90vw)] flex-col gap-4 overflow-y-auto rounded-none"
      >
        <p className="text-foreground text-sm font-semibold break-words">{check.name}</p>
        {check.description && (
          <div className="flex flex-col gap-1.5">
            <span className="kazan-grid-label">{t("checks.fields.description")}</span>
            <p className="text-foreground/85 text-sm leading-relaxed break-words whitespace-pre-line">
              {check.description}
            </p>
          </div>
        )}
        <div className="flex flex-col gap-1.5">
          <span className="kazan-grid-label">{t("checks.fields.expected")}</span>
          <p className="text-foreground/85 text-sm leading-relaxed break-words whitespace-pre-line">
            {check.expected}
          </p>
        </div>
      </PopoverContent>
    </Popover>
  );
}

/**
 * Columns of the check list view. The name column takes the free width and
 * cuts the name and description to one line each, with the full text one
 * click away; every other column has a fixed width so the rows line up.
 * Outcome and status are selects for an organization admin and read-only
 * chips for every other reader, who may not change them.
 */
export function useCheckColumns({
  canManage,
  onEdit,
  onDelete,
  onOutcomeChange,
  onStatusChange,
  savingCheckIds,
}: CheckColumnsOptions) {
  const t = useTranslations("cyberscore");
  const dates = useDateFormat();

  return useMemo<ColumnDef<CheckTableRow, unknown>[]>(
    () => [
      {
        id: "name",
        accessorKey: "name",
        header: t("checks.columns.name"),
        meta: { flex: true },
        sortingFn: "alphanumeric",
        cell: ({ row }) => {
          const { reference, title } = splitCheckName(row.original.name);
          return (
            <div className="flex min-w-0 items-center gap-2">
              {/* The text sits out of flow, so a long name cannot widen the
                  column; the column takes the table's free width instead. */}
              <div className="relative h-10 min-w-0 flex-1">
                <div className="absolute inset-0 flex min-w-0 flex-col justify-center gap-0.5">
                  <span className="flex min-w-0 items-center gap-2">
                    {reference && (
                      <span className="text-muted-foreground shrink-0 font-mono text-xs font-semibold tracking-[0.08em] uppercase">
                        {reference}
                      </span>
                    )}
                    <span className="text-foreground truncate text-sm font-medium">{title}</span>
                  </span>
                  <span className="text-muted-foreground truncate text-xs">
                    {row.original.description ?? row.original.expected}
                  </span>
                </div>
              </div>
              <CheckTextPopover check={row.original} />
            </div>
          );
        },
      },
      {
        id: "category",
        accessorFn: (row) => row.category ?? "",
        header: t("checks.columns.category"),
        size: 150,
        cell: ({ row }) => (
          <span className="text-muted-foreground block truncate text-xs">
            {t(`categories.${row.original.category ?? "NONE"}`)}
          </span>
        ),
      },
      {
        id: "weight",
        accessorKey: "weight",
        header: t("checks.columns.weight"),
        size: 76,
        meta: { align: "center" },
        cell: ({ row }) => (
          <span className="kazan-grid-number h-5 w-auto min-w-5 px-1">{row.original.weight}</span>
        ),
      },
      {
        id: "outcome",
        accessorKey: "outcome",
        header: t("checks.columns.outcome"),
        size: 160,
        cell: ({ row }) =>
          canManage ? (
            <CheckOutcomeSelect
              value={row.original.outcome}
              checkName={row.original.name}
              disabled={savingCheckIds.has(row.original.id)}
              onChange={(outcome) => onOutcomeChange(row.original, outcome)}
            />
          ) : (
            <CheckOutcomeChip outcome={row.original.outcome} />
          ),
      },
      {
        id: "status",
        accessorKey: "status",
        header: t("checks.columns.status"),
        size: 160,
        cell: ({ row }) =>
          canManage ? (
            <CheckStatusSelect
              value={row.original.status}
              checkName={row.original.name}
              disabled={savingCheckIds.has(row.original.id)}
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
        size: 108,
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
              header: () => <span className="sr-only">{t("checks.columns.actions")}</span>,
              size: 84,
              enableSorting: false,
              cell: ({ row }) => (
                <div className="flex items-center justify-end gap-1">
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    aria-label={`${t("checks.edit")}: ${row.original.name}`}
                    title={t("checks.edit")}
                    onClick={() => onEdit(row.original)}
                  >
                    <Pencil className="h-4 w-4" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    aria-label={`${t("checks.delete")}: ${row.original.name}`}
                    title={t("checks.delete")}
                    className="text-destructive hover:text-destructive hover:[&_svg]:text-destructive"
                    onClick={() => onDelete(row.original)}
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </div>
              ),
            } satisfies ColumnDef<CheckTableRow, unknown>,
          ]
        : []),
    ],
    [t, dates, canManage, onEdit, onDelete, onOutcomeChange, onStatusChange, savingCheckIds]
  );
}
