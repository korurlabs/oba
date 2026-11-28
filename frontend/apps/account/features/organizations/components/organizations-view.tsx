"use client";

import { useTranslations } from "next-intl";
import { Plus } from "lucide-react";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { OrganizationRoleBadge } from "@oba/shared/components/organization-role-badge";
import { Button } from "@oba/ui/sky/button";
import { Card, CardContent } from "@oba/ui/sky/card";
import { ListToolbar } from "@oba/ui/sky/list-toolbar";
import { PageHeading } from "@oba/ui/sky/page-heading";
import { EmptyState, LoadError } from "@oba/ui/sky/page-states";

/**
 * Organizations screen: the signed-in user's memberships with their role in
 * each, read-only, under a toolbar with the link to create an organization.
 * The memberships come from the current-user ("me") query, which the
 * dashboard layout prefetches. A user belongs to a handful of organizations,
 * so the list has no search.
 */
export function OrganizationsView() {
  const t = useTranslations();
  const { data: me, isPending, isFetching, refetch } = useGetMe();

  // A failed refetch keeps the last loaded user in the cache, so only a first
  // fetch that failed leaves no user.
  const user = me?.data;
  const organizations = user?.organizations ?? [];

  // Any signed-in user may create an organization; they become its owner.
  const createButton = (
    <Button asChild>
      <Link href="/organizations/new">
        <Plus aria-hidden="true" />
        {t("organization.new.title")}
      </Link>
    </Button>
  );

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
        <>
          <ListToolbar create={createButton} />
          {organizations.length === 0 ? (
            <EmptyState
              title={t("organization.noOrganizations")}
              description={t("organization.noOrganizationsDescription")}
              action={createButton}
            />
          ) : (
            <Card>
              <CardContent>
                <ul className="divide-y" aria-label={t("organization.list.title")}>
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
              </CardContent>
            </Card>
          )}
        </>
      )}
    </div>
  );
}
