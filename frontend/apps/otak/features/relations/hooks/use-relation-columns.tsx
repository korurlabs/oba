"use client";

import { useMemo } from "react";
import { useFormatter, useTranslations } from "next-intl";
import type { ColumnDef } from "@tanstack/react-table";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { Badge } from "@oba/ui/sky/badge";
import { RelationStageBadge } from "../components/relation-stage-badge";
import type { RelationListItem } from "../schemas";

/** Euros without cents for a whole amount, so the column is not padded with ",00". */
function formatEuros(format: ReturnType<typeof useFormatter>, value: number): string {
  const fractionDigits = Number.isInteger(value) ? 0 : 2;
  return format.number(value, {
    style: "currency",
    currency: "EUR",
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  });
}

/**
 * A contact address as a mail link, or a muted dash with screen-reader text
 * when the relation has no such contact. The link stops the click so the row
 * does not open the relation as well.
 */
function MailCell({ email, emptyLabel }: { email: string | null; emptyLabel: string }) {
  if (!email) {
    return (
      <span className="text-muted-foreground text-sm">
        <span aria-hidden="true">-</span>
        <span className="sr-only">{emptyLabel}</span>
      </span>
    );
  }
  return (
    <a
      href={`mailto:${email}`}
      title={email}
      onClick={(event) => event.stopPropagation()}
      className="text-primary block truncate text-sm hover:underline"
    >
      {email}
    </a>
  );
}

interface RelationColumnsOptions {
  /** Leads and customers have a stage; suppliers have none, so the column is left out. */
  showStage: boolean;
  /**
   * A table that mixes types, such as an event's relations, shows each row's
   * type; a list of one type leaves the column out.
   */
  showType?: boolean;
}

/**
 * The relations table. The name takes the free width; every other column has
 * a fixed width sized to its widest usual value, so the columns line up from
 * one tab to the next. Money is right aligned in tabular figures, and the
 * last change always shows date and time, so two changes on one day still
 * tell apart.
 */
export function useRelationColumns({ showStage, showType = false }: RelationColumnsOptions) {
  const t = useTranslations("relations");
  const format = useFormatter();
  const { formatDateTime } = useDateFormat();

  return useMemo<ColumnDef<RelationListItem, unknown>[]>(
    () => [
      {
        id: "name",
        accessorKey: "name",
        header: t("columns.name"),
        // A link, so the keyboard reaches the relation; the row's click is for the mouse.
        cell: ({ row }) => (
          <Link
            href={`/relations/${row.original.id}`}
            onClick={(event) => event.stopPropagation()}
            className="text-foreground block truncate text-sm font-medium hover:underline"
          >
            {row.original.name}
          </Link>
        ),
        meta: { flex: true },
      },
      ...(showType
        ? [
            {
              id: "type",
              accessorKey: "type",
              header: t("columns.type"),
              size: 120,
              cell: ({ row }) => (
                <Badge variant="secondary">{t(`types.${row.original.type}`)}</Badge>
              ),
            } satisfies ColumnDef<RelationListItem, unknown>,
          ]
        : []),
      ...(showStage
        ? [
            {
              id: "stage",
              accessorKey: "stage",
              header: t("columns.stage"),
              size: 140,
              cell: ({ row }) =>
                row.original.stage ? <RelationStageBadge stage={row.original.stage} /> : null,
            } satisfies ColumnDef<RelationListItem, unknown>,
          ]
        : []),
      {
        id: "owner",
        accessorKey: "owner",
        header: t("columns.owner"),
        size: 180,
        cell: ({ row }) =>
          row.original.owner ? (
            <span
              className="text-foreground block truncate text-sm"
              title={row.original.owner.name}
            >
              {row.original.owner.name}
            </span>
          ) : (
            <span className="text-muted-foreground text-sm italic">{t("noOwner")}</span>
          ),
      },
      {
        id: "mainContact",
        accessorKey: "mainPersonContactName",
        header: t("columns.mainContact"),
        size: 180,
        cell: ({ row }) =>
          row.original.mainPersonContactName ? (
            <span
              className="text-foreground block truncate text-sm"
              title={row.original.mainPersonContactName}
            >
              {row.original.mainPersonContactName}
            </span>
          ) : (
            <span className="text-muted-foreground text-sm">
              <span aria-hidden="true">-</span>
              <span className="sr-only">{t("noMainContact")}</span>
            </span>
          ),
      },
      {
        id: "contactMail",
        accessorKey: "mainPersonContactEmail",
        header: t("columns.contactMail"),
        size: 200,
        cell: ({ row }) => (
          <MailCell email={row.original.mainPersonContactEmail} emptyLabel={t("noContactMail")} />
        ),
      },
      {
        id: "companyMail",
        accessorKey: "mainBusinessContactEmail",
        header: t("columns.companyMail"),
        size: 200,
        cell: ({ row }) => (
          <MailCell email={row.original.mainBusinessContactEmail} emptyLabel={t("noCompanyMail")} />
        ),
      },
      {
        id: "expectedValue",
        accessorKey: "expectedValue",
        header: t("columns.expectedValue"),
        size: 140,
        meta: { align: "right" },
        cell: ({ row }) =>
          row.original.expectedValue === null ? (
            <span className="text-muted-foreground text-sm">
              <span aria-hidden="true">-</span>
              <span className="sr-only">{t("noValue")}</span>
            </span>
          ) : (
            <span className="text-foreground text-sm whitespace-nowrap tabular-nums">
              {formatEuros(format, row.original.expectedValue)}
            </span>
          ),
      },
      {
        id: "updatedAt",
        accessorKey: "updatedAt",
        header: t("columns.updatedAt"),
        size: 170,
        cell: ({ row }) => (
          <span className="text-muted-foreground text-sm whitespace-nowrap tabular-nums">
            {formatDateTime(row.original.updatedAt)}
          </span>
        ),
      },
    ],
    [t, format, formatDateTime, showStage, showType]
  );
}
