"use client";

import { useTranslations } from "next-intl";
import { Plus } from "lucide-react";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { OrganizationRoleBadge } from "@oba/shared/components/organization-role-badge";
import { Button } from "@oba/ui/sky/button";
import { Card, CardAction, CardContent, CardHeader } from "@oba/ui/sky/card";
import { LoadError } from "@oba/ui/sky/page-states";
import { PageHeading, PanelTitle } from "@/components/layout/page-heading";

/**
 * Organizations screen: the signed-in user's memberships with their role in
 * each, read-only, and a link to create an organization. The memberships come
 * from the current-user ("me") query, which the dashboard layout prefetches.
 */
export function OrganizationsView() {
  const t = useTranslations();
  const { data: me, isPending, isFetching, refetch } = useGetMe();

  // A failed refetch keeps the last loaded user in the cache, so only a first
  // fetch that failed leaves no user.
  const user = me?.data;
  const organizations = user?.organizations ?? [];

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6">
      <PageHeading
        title={t("organization.list.title")}
        description={t("organization.list.subtitle")}
      />

      {isPending ? (
        <div className="flex flex-col gap-2" aria-busy="true">
          <span className="sr-only">{t("organization.list.loading")}</span>
          <div className="bg-muted h-24 animate-pulse rounded-xl" />
        </div>
      ) : !user ? (
        <LoadError
          title={t("errors.somethingWentWrong")}
          message={t("errors.unexpectedError")}
          retryLabel={t("errors.tryAgain")}
          onRetry={() => {
            if (!isFetching) void refetch();
          }}
        />
      ) : (
        <Card role="region" aria-labelledby="organizations-list-heading">
          <CardHeader>
            <PanelTitle id="organizations-list-heading">
              {t("organization.list.yourOrganizations")}
            </PanelTitle>
            <CardAction>
              {/* Any signed-in user may create an organization; they become its owner. */}
              <Button asChild size="sm">
                <Link href="/organizations/new">
                  <Plus aria-hidden="true" />
                  {t("organization.new.title")}
                </Link>
              </Button>
            </CardAction>
          </CardHeader>
          <CardContent>
            {organizations.length === 0 ? (
              <p className="text-muted-foreground text-sm">{t("organization.noOrganizations")}</p>
            ) : (
              <ul className="divide-y">
                {organizations.map((organization) => (
                  <li
                    key={organization.id}
                    className="flex items-center justify-between gap-4 py-2 text-sm"
                  >
                    <span className="min-w-0 truncate font-medium">{organization.name}</span>
                    <OrganizationRoleBadge role={organization.role} />
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
