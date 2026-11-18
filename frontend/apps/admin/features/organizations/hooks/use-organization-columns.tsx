"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import type { ColumnDef } from "@tanstack/react-table";
import type { UserOrganization } from "@oba/shared/features/users/schema";
import { OrganizationRoleBadge } from "@oba/shared/components/organization-role-badge";
import { Link } from "@oba/shared/libs/i18n/navigation";

export function useOrganizationColumns() {
  const t = useTranslations("organization");

  return useMemo<ColumnDef<UserOrganization, unknown>[]>(
    () => [
      {
        id: "name",
        accessorKey: "name",
        header: t("name"),
        // The name opens the organization, as a list row links its item on Kaya's pages.
        cell: ({ row }) => (
          <Link
            href={`/organizations/${row.original.id}`}
            className="font-medium hover:underline"
          >
            {row.original.name}
          </Link>
        ),
      },
      {
        id: "role",
        accessorKey: "role",
        header: t("role"),
        cell: ({ row }) => <OrganizationRoleBadge role={row.original.role} />,
      },
    ],
    [t],
  );
}
