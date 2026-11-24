"use client";

import { useTranslations } from "next-intl";
import { Building2 } from "lucide-react";
import { OrganizationSelector } from "@oba/shared/components/app-header/organization-selector";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import { managedOrganizations } from "@oba/shared/features/organizations/lib/managed-organizations";
import { TableLoading } from "@oba/ui/sky/page-states";
import { useOrganizationId } from "@/hooks/use-organization-id";

/**
 * Renders the page only when the current organization is one the user
 * manages: every Ulak page administers that organization, and the API refuses
 * its admin calls to anyone else. Otherwise the user chooses one of the
 * organizations they manage here, which makes it the default in every app.
 *
 * The check reads the same organization the queries are keyed on, and the
 * dashboard layout hydrates the current user, so the server render already
 * shows the right screen. After a failed load of the user the page renders
 * and shows its own error state: a short API failure never locks out a
 * manager, and the API still refuses whoever is not one.
 */
export function OrganizationGate({ children }: { children: React.ReactNode }) {
  const t = useTranslations();
  const organizationId = useOrganizationId();
  const { data: meResponse, isPending, isError } = useGetMe();

  if (organizationId && isPending) {
    return <TableLoading label={t("organization.loadingOrganization")} />;
  }

  const isManaged =
    organizationId !== null &&
    (isError ||
      managedOrganizations(meResponse?.data).some(
        (organization) => organization.id === organizationId
      ));

  if (isManaged) {
    return <>{children}</>;
  }

  return (
    <div className="flex min-h-full items-center justify-center p-4 py-16">
      <div className="w-full max-w-md space-y-6 text-center">
        <div className="flex justify-center">
          <div className="bg-primary/10 rounded-full p-4">
            <Building2 className="text-primary h-12 w-12" aria-hidden="true" />
          </div>
        </div>
        <div className="space-y-2">
          <h1 className="text-foreground text-2xl font-bold tracking-tight">
            {t("organizationGate.title")}
          </h1>
          <p className="text-muted-foreground text-sm">{t("organizationGate.description")}</p>
        </div>
        <div className="flex justify-center">
          <OrganizationSelector managedOnly className="w-full max-w-xs" />
        </div>
      </div>
    </div>
  );
}
