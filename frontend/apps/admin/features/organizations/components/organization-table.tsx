"use client";

import { useTranslations } from "next-intl";
import { Plus } from "lucide-react";
import { Button } from "@oba/ui/sky/button";
import { EmptyState } from "@oba/ui/sky/page-states";
import { ACCOUNT_LINKS } from "@oba/shared/constants/apps";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import { managedOrganizations } from "@oba/shared/features/organizations/lib/managed-organizations";
import type { UserOrganization } from "@oba/shared/features/users/schema";
import { DataTable } from "@/components/data-table";
import { PageHeader } from "@/components/layout/page-header";
import { useOrganizationColumns } from "../hooks/use-organization-columns";
import { OrganizationLoadError } from "./organization-load-error";

export function OrganizationTable() {
  const t = useTranslations("organization");
  const tSidebar = useTranslations("sidebar");
  const { data: meResponse, isPending, isError, refetch } = useGetMe();
  const columns = useOrganizationColumns();

  // Only the organizations the user may change: the API refuses every admin
  // call on any other one.
  const organizations: UserOrganization[] = managedOrganizations(meResponse?.data);

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={t("listTitle")}
        description={t("listDescription")}
        actions={
          // Organizations are created in the account app, another origin.
          <Button asChild>
            <a href={ACCOUNT_LINKS.newOrganization}>
              <Plus aria-hidden="true" />
              {tSidebar("newOrganization")}
            </a>
          </Button>
        }
      />

      {/* A current user that never loaded must not be shown as an empty list;
          a failed background refetch keeps the cached organizations. */}
      {isError && !meResponse ? (
        <OrganizationLoadError onRetry={() => void refetch()} />
      ) : (
        <DataTable<UserOrganization>
          columns={columns}
          data={organizations}
          isLoading={isPending}
          loadingLabel={t("loading")}
          getRowId={(row: UserOrganization) => row.id}
          emptyMessage={
            <EmptyState
              title={t("noOrganizations")}
              description={t("noOrganizationsDescription")}
            />
          }
        />
      )}
    </div>
  );
}
