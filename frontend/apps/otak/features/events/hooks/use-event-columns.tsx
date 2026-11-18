"use client";

import { useMemo } from "react";
import { useLocale, useTranslations } from "next-intl";
import type { ColumnDef } from "@tanstack/react-table";
import { Pencil, Trash2 } from "lucide-react";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { Badge } from "@oba/ui/sky/badge";
import { Button } from "@oba/ui/sky/button";
import { formatEventDates, formatEventPlace } from "../lib/event-format";
import type { EventListItem } from "../schemas";

interface EventColumnsOptions {
  onEdit: (event: EventListItem) => void;
  /** Null hides the delete action: only an admin may delete. */
  onDelete: ((event: EventListItem) => void) | null;
}

/**
 * The event table. The name takes the free width and links to the event
 * page; the other columns have fixed widths. The dates are calendar days
 * shown the same for every viewer, a missing place or website a muted dash.
 */
export function useEventColumns({ onEdit, onDelete }: EventColumnsOptions) {
  const t = useTranslations("events");
  const locale = useLocale();
  const countryNames = useMemo(() => new Intl.DisplayNames([locale], { type: "region" }), [locale]);

  return useMemo<ColumnDef<EventListItem, unknown>[]>(() => {
    const none = (label: string) => (
      <span className="text-muted-foreground text-sm">
        <span aria-hidden="true">-</span>
        <span className="sr-only">{label}</span>
      </span>
    );

    return [
      {
        id: "name",
        accessorKey: "name",
        header: t("columns.name"),
        // A link, so the keyboard reaches the event; the row's click is for the mouse.
        cell: ({ row }) => (
          <Link
            href={`/events/${row.original.id}`}
            onClick={(event) => event.stopPropagation()}
            className="text-foreground block truncate text-sm font-medium hover:underline"
          >
            {row.original.name}
          </Link>
        ),
        meta: { flex: true },
      },
      {
        id: "type",
        accessorKey: "type",
        header: t("columns.type"),
        size: 130,
        cell: ({ row }) => <Badge variant="secondary">{t(`types.${row.original.type}`)}</Badge>,
      },
      {
        id: "dates",
        accessorKey: "startDate",
        header: t("columns.dates"),
        size: 230,
        cell: ({ row }) => (
          <span className="text-sm whitespace-nowrap tabular-nums">
            {formatEventDates(row.original.startDate, row.original.endDate, locale)}
          </span>
        ),
      },
      {
        id: "place",
        header: t("columns.place"),
        size: 200,
        cell: ({ row }) => {
          const place = formatEventPlace(
            row.original.city,
            row.original.country,
            (code) => countryNames.of(code) ?? code
          );
          return place ? (
            <span className="block truncate text-sm" title={place}>
              {place}
            </span>
          ) : (
            none(t("noPlace"))
          );
        },
      },
      {
        id: "website",
        accessorKey: "website",
        header: t("columns.website"),
        size: 200,
        cell: ({ row }) =>
          row.original.website ? (
            <a
              href={row.original.website}
              target="_blank"
              rel="noopener noreferrer"
              onClick={(event) => event.stopPropagation()}
              className="text-foreground block truncate text-sm hover:underline"
              title={row.original.website}
            >
              {row.original.website.replace(/^https?:\/\//, "")}
            </a>
          ) : (
            none(t("noWebsite"))
          ),
      },
      {
        id: "actions",
        header: () => <span className="sr-only">{t("columns.actions")}</span>,
        size: onDelete ? 88 : 56,
        cell: ({ row }) => (
          <div className="flex justify-end gap-1">
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label={t("edit", { name: row.original.name })}
              onClick={(event) => {
                event.stopPropagation();
                onEdit(row.original);
              }}
            >
              <Pencil aria-hidden="true" />
            </Button>
            {onDelete && (
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label={t("delete", { name: row.original.name })}
                onClick={(event) => {
                  event.stopPropagation();
                  onDelete(row.original);
                }}
              >
                <Trash2 aria-hidden="true" />
              </Button>
            )}
          </div>
        ),
      },
    ];
  }, [t, locale, countryNames, onEdit, onDelete]);
}
