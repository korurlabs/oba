"use client";

import { useMemo, useState } from "react";
import { useTranslations } from "next-intl";
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
import { Button } from "@oba/ui/sky/button";
import { MoreVertical, Pencil, Trash2 } from "lucide-react";
import type { ColumnDef } from "@tanstack/react-table";
import { getCountryOptions } from "@oba/shared/features/organizations/lib/organization-options";
import type { LegalEntityListItem } from "../schemas";

interface LegalEntityColumnsOptions {
  /** Without it the table is read-only: the actions column is left out. */
  canEdit: boolean;
  onEdit: (entity: LegalEntityListItem) => void;
  onDelete: (legalEntityId: string) => void;
  isDeleting: boolean;
}

/**
 * The country's name for the codes the organization form offers. A code set
 * through the API outside that list is shown as it is stored.
 */
function useCountryName() {
  const tOnboarding = useTranslations("onboarding");
  return useMemo(() => {
    const names = new Map(
      getCountryOptions(tOnboarding).map((option) => [String(option.value), option.label])
    );
    return (code: string) => names.get(code) ?? code;
  }, [tOnboarding]);
}

export function useLegalEntityColumns({
  canEdit,
  onEdit,
  onDelete,
  isDeleting,
}: LegalEntityColumnsOptions) {
  const t = useTranslations("organization");
  const countryName = useCountryName();

  return useMemo<ColumnDef<LegalEntityListItem, unknown>[]>(
    () => [
      {
        id: "shortName",
        accessorKey: "shortName",
        header: t("legalEntities.shortName"),
        cell: ({ row }) => (
          <span className="text-foreground block max-w-[200px] truncate text-sm font-medium">
            {row.original.shortName}
          </span>
        ),
      },
      {
        id: "legalName",
        accessorKey: "legalName",
        header: t("legalEntities.legalName"),
        meta: { flex: true },
        cell: ({ row }) => (
          <span className="text-foreground block max-w-[320px] truncate text-sm">
            {row.original.legalName}
          </span>
        ),
      },
      {
        id: "country",
        accessorKey: "country",
        header: t("legalEntities.country"),
        cell: ({ row }) => (
          <span className="text-foreground text-sm">{countryName(row.original.country)}</span>
        ),
      },
      {
        id: "registrationNumber",
        accessorKey: "registrationNumber",
        header: t("legalEntities.registrationNumber"),
        cell: ({ row }) => (
          <span className="text-muted-foreground text-sm tabular-nums">
            {row.original.registrationNumber ?? "-"}
          </span>
        ),
      },
      ...(canEdit
        ? [
            {
              id: "actions",
              header: "",
              size: 50,
              cell: ({ row }) => (
                <LegalEntityActions
                  entity={row.original}
                  onEdit={onEdit}
                  onDelete={onDelete}
                  isDeleting={isDeleting}
                />
              ),
            } satisfies ColumnDef<LegalEntityListItem, unknown>,
          ]
        : []),
    ],
    [t, countryName, canEdit, onEdit, onDelete, isDeleting]
  );
}

/** Row menu of a legal entity: edit, and delete after a confirmation. */
function LegalEntityActions({
  entity,
  onEdit,
  onDelete,
  isDeleting,
}: {
  entity: LegalEntityListItem;
  onEdit: (entity: LegalEntityListItem) => void;
  onDelete: (legalEntityId: string) => void;
  isDeleting: boolean;
}) {
  const t = useTranslations("organization");
  const [confirmOpen, setConfirmOpen] = useState(false);

  // The confirmation sits beside the menu, not inside it: the menu unmounts
  // its items when it closes, which would take an open dialog with it.
  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="ghost" size="icon-sm" onClick={(e) => e.stopPropagation()}>
            <MoreVertical aria-hidden="true" />
            <span className="sr-only">{t("legalEntities.actions")}</span>
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem onSelect={() => onEdit(entity)}>
            <Pencil aria-hidden="true" />
            {t("legalEntities.edit")}
          </DropdownMenuItem>
          <DropdownMenuSeparator />
          <DropdownMenuItem
            variant="destructive"
            disabled={isDeleting}
            onSelect={() => setConfirmOpen(true)}
          >
            <Trash2 aria-hidden="true" />
            {t("legalEntities.delete")}
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
      <Dialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("legalEntities.deleteConfirmTitle")}</DialogTitle>
            <DialogDescription>
              {t("legalEntities.deleteConfirmDescription", { name: entity.shortName })}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">{t("legalEntities.cancel")}</Button>
            </DialogClose>
            <DialogClose asChild>
              <Button variant="destructive" onClick={() => onDelete(entity.id)}>
                {t("legalEntities.delete")}
              </Button>
            </DialogClose>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
