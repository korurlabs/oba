"use client";

import { Suspense } from "react";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { useTranslations } from "next-intl";
import { ArrowLeft } from "lucide-react";
import { Button } from "@oba/ui/sky/button";
import { EmptyState, TableLoading } from "@oba/ui/sky/page-states";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@oba/ui/sky/tabs";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import { useGetOrganization } from "@/features/organizations/hooks/use-organizations";
import {
  useOrgDetailTab,
  type OrgDetailTab,
} from "@/features/organizations/hooks/use-org-detail-tab";
import { OrganizationDetailForm } from "@/features/organizations/components/organization-detail-form";
import { MembersTab } from "@/features/organizations/components/organization-members-tab";
import { InvitationsTab } from "@/features/organizations/components/organization-invitations-tab";
import { LegalEntitiesTab } from "@/features/organizations/components/organization-legal-entities-tab";
import { GroupsTab } from "@/features/organizations/components/organization-groups-tab";
import { AccessTab } from "@/features/organizations/components/organization-access-tab";
import { OrganizationLoadError } from "@/features/organizations/components/organization-load-error";
import { PageHeader } from "@/components/layout/page-header";
import type {
  OrganizationDetail,
  OrganizationRole,
} from "@oba/shared/features/organizations/schemas";

/**
 * Organization Detail View (client)
 *
 * Tabbed layout: Detail (form), Members (list), Invitations (list + create),
 * Legal entities (list), and for OWNER/ADMIN also Groups and Access (app
 * grants).
 * OWNER/ADMIN get edit controls; MEMBER/GUEST see read-only views and no
 * Groups or Access tab, because the API lets only OWNER/ADMIN read them.
 *
 * Rendered by the thin async server page, which server-prefetches the
 * useGetOrganization(id) detail query into the React Query cache.
 *
 * The role comes from the current-user ("me") query. While that query is
 * loading the skeleton is shown, and when no user could be loaded an error
 * with a retry is shown in place of the tabs, so "role unknown" is never
 * rendered as "read-only". A failed background refetch keeps the cached user,
 * whose role is still known, so the tabs stay.
 */
export function OrganizationDetailView({ id }: { id: string }) {
  const t = useTranslations("organization");
  const {
    data: meResponse,
    isPending: isMePending,
    isError: isMeError,
    refetch: refetchMe,
  } = useGetMe();

  const {
    data: orgResponse,
    isLoading: isOrgLoading,
    isError: isOrgError,
  } = useGetOrganization(id);

  // Derive role from useGetMe organizations array
  const organizations = meResponse?.data?.organizations ?? [];
  const currentRole = organizations.find((o) => o.id === id)?.role;
  const canEdit = currentRole === "OWNER" || currentRole === "ADMIN";

  const orgData = orgResponse?.data;
  const hasNoUser = isMeError && !meResponse;

  // Show loading
  if (isOrgLoading || isMePending) {
    return <TableLoading label={t("loadingOrganization")} />;
  }

  // Organization not found (deleted or no access)
  if (isOrgError || (!isOrgLoading && !orgData)) {
    return (
      <div className="flex flex-col gap-6">
        <BackToOrganizations />
        <EmptyState title={t("notFound")} description={t("notFoundDescription")} />
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <BackToOrganizations />

      <PageHeader title={orgData?.name} description={t("description")} />

      {/* Tab layout -- Suspense required for useSearchParams */}
      <Suspense fallback={<TableLoading label={t("loadingOrganization")} />}>
        {hasNoUser ? (
          <OrganizationLoadError onRetry={() => void refetchMe()} />
        ) : (
          <OrganizationDetailTabs
            id={id}
            orgData={orgData}
            canEdit={canEdit}
            currentUserRole={currentRole}
          />
        )}
      </Suspense>
    </div>
  );
}

/** The way back to the list, as Kaya's detail pages open. */
function BackToOrganizations() {
  const t = useTranslations("organization");

  return (
    <Button asChild variant="ghost" size="sm" className="w-fit">
      <Link href="/">
        <ArrowLeft aria-hidden="true" />
        {t("backToOrganizations")}
      </Link>
    </Button>
  );
}

function OrganizationDetailTabs({
  id,
  orgData,
  canEdit,
  currentUserRole,
}: {
  id: string;
  orgData: OrganizationDetail | undefined;
  canEdit: boolean;
  currentUserRole: OrganizationRole | undefined;
}) {
  const t = useTranslations("organization");
  const { activeTab, setTab } = useOrgDetailTab({ canManage: canEdit });

  return (
    <Tabs
      value={activeTab}
      onValueChange={(v) => setTab(v as OrgDetailTab)}
    >
      <TabsList>
        <TabsTrigger value="detail">{t("tabs.detail")}</TabsTrigger>
        <TabsTrigger value="members">{t("tabs.members")}</TabsTrigger>
        <TabsTrigger value="invitations">
          {t("tabs.invitations")}
        </TabsTrigger>
        <TabsTrigger value="legalEntities">{t("tabs.legalEntities")}</TabsTrigger>
        {canEdit && (
          <>
            <TabsTrigger value="groups">{t("tabs.groups")}</TabsTrigger>
            <TabsTrigger value="access">{t("tabs.access")}</TabsTrigger>
          </>
        )}
      </TabsList>

      <TabsContent value="detail" className="mt-2">
        <OrganizationDetailForm
          organizationId={id}
          orgData={orgData}
          canEdit={canEdit}
        />
      </TabsContent>

      <TabsContent value="members" className="mt-2">
        <MembersTab organizationId={id} canEdit={canEdit} />
      </TabsContent>

      <TabsContent value="invitations" className="mt-2">
        <InvitationsTab organizationId={id} canEdit={canEdit} currentUserRole={currentUserRole} />
      </TabsContent>

      <TabsContent value="legalEntities" className="mt-2">
        <LegalEntitiesTab organizationId={id} canEdit={canEdit} />
      </TabsContent>

      {canEdit && (
        <>
          <TabsContent value="groups" className="mt-2">
            <GroupsTab organizationId={id} />
          </TabsContent>

          <TabsContent value="access" className="mt-2">
            <AccessTab organizationId={id} />
          </TabsContent>
        </>
      )}
    </Tabs>
  );
}
