"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import type { ColumnDef } from "@tanstack/react-table";
import type { MemberAppAccess } from "@oba/shared/features/organizations/schemas";
import { OrganizationRoleBadge } from "@oba/shared/components/organization-role-badge";
import { AppBadges } from "../components/app-badges";

/** Columns of the effective access table: one row per active member. */
export function useAppAccessColumns() {
  const t = useTranslations("organization");

  return useMemo<ColumnDef<MemberAppAccess, unknown>[]>(
    () => [
      {
        id: "fullName",
        accessorKey: "fullName",
        header: t("access.member"),
        meta: { flex: true },
        cell: ({ row }) => (
          <span className="text-foreground block max-w-[240px] truncate text-sm font-medium">
            {row.original.fullName}
          </span>
        ),
      },
      {
        id: "role",
        accessorKey: "role",
        header: t("access.role"),
        cell: ({ row }) => <OrganizationRoleBadge role={row.original.role} />,
      },
      {
        id: "apps",
        accessorKey: "apps",
        header: t("access.apps"),
        cell: ({ row }) => <AppBadges apps={row.original.apps} emptyLabel={t("access.noApps")} />,
      },
    ],
    [t]
  );
}
