"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import type { ColumnDef } from "@tanstack/react-table";
import { Pencil, Trash2 } from "lucide-react";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { Badge } from "@oba/ui/sky/badge";
import { Button } from "@oba/ui/sky/button";
import { CampaignStatusBadge } from "../components/campaign-status-badge";
import type { CampaignContent } from "../schemas";

interface CampaignContentColumnsOptions {
  onEdit: (content: CampaignContent) => void;
  onDelete: (content: CampaignContent) => void;
}

/**
 * The contents table of a campaign. A content has no title, so a row is told
 * apart by its status, channel, language, legal entity and moments. The
 * moments show in the viewer's zone; a missing one is a muted dash. Images
 * are shown only in the content dialog.
 */
export function useCampaignContentColumns({ onEdit, onDelete }: CampaignContentColumnsOptions) {
  const t = useTranslations("campaigns");
  const tTemplates = useTranslations("documentTemplates");
  const { formatDateTime } = useDateFormat();

  return useMemo<ColumnDef<CampaignContent, unknown>[]>(() => {
    const moment = (value: string | null, emptyLabel: string) => {
      if (value === null) {
        return (
          <span className="text-muted-foreground text-sm">
            <span aria-hidden="true">-</span>
            <span className="sr-only">{emptyLabel}</span>
          </span>
        );
      }
      return <span className="text-sm whitespace-nowrap tabular-nums">{formatDateTime(value)}</span>;
    };

    return [
      {
        id: "status",
        header: t("contentColumns.status"),
        size: 120,
        cell: ({ row }) => <CampaignStatusBadge status={row.original.status} />,
      },
      {
        id: "channel",
        header: t("contentColumns.channel"),
        size: 120,
        cell: ({ row }) => (
          <Badge variant="secondary">{t(`channels.${row.original.channel}`)}</Badge>
        ),
      },
      {
        id: "language",
        header: t("contentColumns.language"),
        size: 120,
        cell: ({ row }) => (
          <span className="text-sm">{tTemplates(`languages.${row.original.language}`)}</span>
        ),
      },
      {
        id: "legalEntity",
        header: t("contentColumns.legalEntity"),
        cell: ({ row }) => (
          <span className="block truncate text-sm" title={row.original.legalEntityShortName}>
            {row.original.legalEntityShortName}
          </span>
        ),
        meta: { flex: true },
      },
      {
        id: "plannedAt",
        header: t("contentColumns.plannedAt"),
        size: 160,
        cell: ({ row }) => moment(row.original.plannedAt, t("notPlanned")),
      },
      {
        id: "publishedAt",
        header: t("contentColumns.publishedAt"),
        size: 160,
        cell: ({ row }) => moment(row.original.publishedAt, t("notPublished")),
      },
      {
        id: "actions",
        header: () => <span className="sr-only">{t("contentColumns.actions")}</span>,
        size: 88,
        cell: ({ row }) => {
          const label = t("contentLabel", {
            channel: t(`channels.${row.original.channel}`),
            language: tTemplates(`languages.${row.original.language}`),
          });
          return (
            <div className="flex justify-end gap-1">
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label={t("edit", { name: label })}
                onClick={(event) => {
                  event.stopPropagation();
                  onEdit(row.original);
                }}
              >
                <Pencil aria-hidden="true" />
              </Button>
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label={t("delete", { name: label })}
                onClick={(event) => {
                  event.stopPropagation();
                  onDelete(row.original);
                }}
              >
                <Trash2 aria-hidden="true" />
              </Button>
            </div>
          );
        },
      },
    ];
  }, [t, tTemplates, formatDateTime, onEdit, onDelete]);
}
