"use client";

import { useMemo } from "react";
import { useLocale, useTranslations } from "next-intl";
import type { ColumnDef } from "@tanstack/react-table";
import { Pencil, Trash2 } from "lucide-react";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { Button } from "@oba/ui/sky/button";
import { CampaignEventLink } from "../components/campaign-event-link";
import { formatCampaignDates } from "../lib/campaign-format";
import type { CampaignListItem } from "../schemas";

interface CampaignColumnsOptions {
  onEdit: (campaign: CampaignListItem) => void;
  /** Null hides the delete action: only an admin may delete. */
  onDelete: ((campaign: CampaignListItem) => void) | null;
}

/**
 * The campaign table. The name takes the free width and links to the
 * campaign page; the other columns have fixed widths. A missing day, owner
 * or event is a muted dash.
 */
export function useCampaignColumns({ onEdit, onDelete }: CampaignColumnsOptions) {
  const t = useTranslations("campaigns");
  const locale = useLocale();

  return useMemo<ColumnDef<CampaignListItem, unknown>[]>(() => {
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
        // A link, so the keyboard reaches the campaign; the row's click is for the mouse.
        cell: ({ row }) => (
          <Link
            href={`/campaigns/${row.original.id}`}
            onClick={(event) => event.stopPropagation()}
            className="text-foreground block truncate text-sm font-medium hover:underline"
          >
            {row.original.name}
          </Link>
        ),
        meta: { flex: true },
      },
      {
        id: "dates",
        header: t("columns.dates"),
        size: 220,
        cell: ({ row }) => {
          const dates = formatCampaignDates(
            row.original.startDate,
            row.original.endDate,
            locale,
            t
          );
          if (dates === null) return none(t("noDates"));
          return <span className="text-sm whitespace-nowrap tabular-nums">{dates}</span>;
        },
      },
      {
        id: "owner",
        header: t("columns.owner"),
        size: 160,
        cell: ({ row }) =>
          row.original.owner ? (
            <span className="block truncate text-sm" title={row.original.owner.name}>
              {row.original.owner.name}
            </span>
          ) : (
            none(t("noOwner"))
          ),
      },
      {
        id: "event",
        header: t("columns.event"),
        size: 180,
        cell: ({ row }) =>
          row.original.eventId ? (
            <CampaignEventLink eventId={row.original.eventId} />
          ) : (
            none(t("noEvent"))
          ),
      },
      {
        id: "contentCount",
        accessorKey: "contentCount",
        header: t("columns.contentCount"),
        size: 100,
        cell: ({ row }) => (
          <span className="text-sm tabular-nums">{row.original.contentCount}</span>
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
  }, [t, locale, onEdit, onDelete]);
}
