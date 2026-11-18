"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import type { ColumnDef } from "@tanstack/react-table";
import { ExternalLink, Pencil, Star, Trash2 } from "lucide-react";
import { Badge } from "@oba/ui/sky/badge";
import { Button } from "@oba/ui/sky/button";
import { ContactKind, type ContactListItem } from "../schemas";

interface ContactColumnsOptions {
  onEdit: (contact: ContactListItem) => void;
  onDelete: (contact: ContactListItem) => void;
  /** Marks the contact as the relation's main one of its kind; offered on every other row. */
  onMakeMain: (contact: ContactListItem) => void;
}

export function useContactColumns({ onEdit, onDelete, onMakeMain }: ContactColumnsOptions) {
  const t = useTranslations("contacts");

  return useMemo<ColumnDef<ContactListItem, unknown>[]>(
    () => [
      {
        id: "name",
        accessorKey: "name",
        header: t("columns.name"),
        cell: ({ row }) => (
          <div className="flex min-w-0 items-center gap-2">
            <span className="text-foreground truncate text-sm font-medium">
              {row.original.name}
            </span>
            {row.original.mainPersonContact && <Badge variant="info">{t("mainContact")}</Badge>}
            {row.original.mainBusinessContact && (
              <Badge variant="info">{t("mainBusinessContact")}</Badge>
            )}
            {row.original.kind === ContactKind.BUSINESS && (
              <Badge variant="outline">{t("business")}</Badge>
            )}
            {row.original.decisionMaker && <Badge variant="secondary">{t("decisionMaker")}</Badge>}
          </div>
        ),
        meta: { flex: true },
      },
      {
        id: "role",
        accessorKey: "role",
        header: t("columns.role"),
        cell: ({ row }) => <span className="text-sm">{row.original.role}</span>,
      },
      {
        id: "email",
        accessorKey: "email",
        header: t("columns.email"),
        cell: ({ row }) =>
          row.original.email ? (
            <div className="flex items-center gap-2">
              <a
                href={`mailto:${row.original.email}`}
                className="text-primary text-sm whitespace-nowrap hover:underline"
              >
                {row.original.email}
              </a>
              {row.original.emailType && (
                <Badge variant="outline">{t(`emailTypes.${row.original.emailType}`)}</Badge>
              )}
            </div>
          ) : null,
      },
      {
        id: "phone",
        accessorKey: "phone",
        header: t("columns.phone"),
        cell: ({ row }) =>
          row.original.phone ? (
            <a
              href={`tel:${row.original.phone.replace(/\s+/g, "")}`}
              className="text-primary text-sm whitespace-nowrap hover:underline"
            >
              {row.original.phone}
            </a>
          ) : null,
      },
      {
        id: "linkedinUrl",
        accessorKey: "linkedinUrl",
        header: t("columns.linkedin"),
        cell: ({ row }) =>
          row.original.linkedinUrl ? (
            <a
              href={row.original.linkedinUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="text-primary inline-flex items-center gap-1 text-sm whitespace-nowrap hover:underline"
            >
              {t("linkedinOpen")}
              <ExternalLink className="h-3 w-3" aria-hidden="true" />
            </a>
          ) : null,
      },
      {
        id: "actions",
        header: () => <span className="sr-only">{t("columns.actions")}</span>,
        cell: ({ row }) => {
          const business = row.original.kind === ContactKind.BUSINESS;
          const isMainOfKind = business
            ? row.original.mainBusinessContact
            : row.original.mainPersonContact;
          return (
            <div className="flex justify-end gap-1">
              {!isMainOfKind && (
                <Button
                  variant="ghost"
                  size="icon-sm"
                  title={business ? t("makeMainBusiness") : t("makeMain")}
                  aria-label={
                    business
                      ? t("makeMainBusinessNamed", { name: row.original.name })
                      : t("makeMainNamed", { name: row.original.name })
                  }
                  onClick={() => onMakeMain(row.original)}
                >
                  <Star aria-hidden="true" />
                </Button>
              )}
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label={t("edit", { name: row.original.name })}
                onClick={() => onEdit(row.original)}
              >
                <Pencil aria-hidden="true" />
              </Button>
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label={t("delete", { name: row.original.name })}
                onClick={() => onDelete(row.original)}
              >
                <Trash2 aria-hidden="true" />
              </Button>
            </div>
          );
        },
      },
    ],
    [t, onEdit, onDelete, onMakeMain]
  );
}
