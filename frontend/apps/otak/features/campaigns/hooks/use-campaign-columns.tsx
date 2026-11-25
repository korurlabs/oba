"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import type { ColumnDef } from "@tanstack/react-table";
import { Pencil, Trash2 } from "lucide-react";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { Badge } from "@oba/ui/sky/badge";
import { Button } from "@oba/ui/sky/button";
import { CampaignStatusBadge } from "../components/campaign-status-badge";
import type { CampaignListItem } from "../schemas";

interface CampaignColumnsOptions {
  onEdit: (campaign: CampaignListItem) => void;
  /** Null hides the delete action: only an admin may delete. */
  onDelete: ((campaign: CampaignListItem) => void) | null;
}

/**
 * The campaign table. The name takes the free width and links to the
 * campaign page; the other columns have fixed widths. The moments show in
 * the viewer's zone; a missing moment or owner is a muted dash.
 */
export function useCampaignColumns({ onEdit, onDelete }: CampaignColumnsOptions) {
  const t = useTranslations("campaigns");
  const { formatDateTime } = useDateFormat();

  return useMemo<ColumnDef<CampaignListItem, unknown>[]>(() => {
    const none = (label: string) => (
      <span className="text-muted-foreground text-sm">
        <span aria-hidden="true">-</span>
        <span className="sr-only">{label}</span>
      </span>
    );
    const moment = (value: string | null, emptyLabel: string) =>
      value ? (
        <span className="text-sm whitespace-nowrap tabular-nums">{formatDateTime(value)}</span>
      ) : (
        none(emptyLabel)
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
        id: "channel",
        accessorKey: "channel",
        header: t("columns.channel"),
        size: 120,
        cell: ({ row }) => (
          <Badge variant="secondary">{t(`channels.${row.original.channel}`)}</Badge>
        ),
      },
      {
        id: "status",
        accessorKey: "status",
        header: t("columns.status"),
        size: 120,
        cell: ({ row }) => <CampaignStatusBadge status={row.original.status} />,
      },
      {
        id: "languages",
        header: t("columns.languages"),
        size: 130,
        cell: ({ row }) =>
          row.original.languages.length > 0 ? (
            <span className="flex gap-1">
              {row.original.languages.map((language) => (
                <Badge key={language} variant="outline">
                  {language}
                </Badge>
              ))}
            </span>
          ) : (
            none(t("noTexts"))
          ),
      },
      {
        id: "plannedAt",
        accessorKey: "plannedAt",
        header: t("columns.plannedAt"),
        size: 160,
        cell: ({ row }) => moment(row.original.plannedAt, t("notPlanned")),
      },
      {
        id: "publishedAt",
        accessorKey: "publishedAt",
        header: t("columns.publishedAt"),
        size: 160,
        cell: ({ row }) => moment(row.original.publishedAt, t("notPublished")),
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
  }, [t, formatDateTime, onEdit, onDelete]);
}
