"use client";

import { useTranslations } from "next-intl";
import { PageHeading } from "@oba/ui/sky/page-heading";
import { EmptyState, TableLoading } from "@oba/ui/sky/page-states";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import type {
  OrganizationDetail,
  OrganizationRole,
} from "@oba/shared/features/organizations/schemas";
import { useGetOrganization } from "@/features/organizations/hooks/use-organizations";
import { OrganizationDetailForm } from "@/features/organizations/components/organization-detail-form";
import { MembersTab } from "@/features/organizations/components/organization-members-tab";
import { InvitationsTab } from "@/features/organizations/components/organization-invitations-tab";
import { LegalEntitiesTab } from "@/features/organizations/components/organization-legal-entities-tab";
import { GroupsTab } from "@/features/organizations/components/organization-groups-tab";
import { AccessTab } from "@/features/organizations/components/organization-access-tab";
import { AgentsTab } from "@/features/agents/components/organization-agents-tab";
import { OrganizationLoadError } from "@/features/organizations/components/organization-load-error";
import { useOrganizationId } from "@/hooks/use-organization-id";

/** The Ulak pages that administer one part of the current organization. */
export type OrganizationSection =
  | "agents"
  | "humans"
  | "groups"
  | "legalEntities"
  | "access"
  | "invitations"
  | "settings";

/** Menu label of each section, under the `sidebar` messages. */
const SECTION_TITLE_KEYS = {
  agents: "agents",
  humans: "humans",
  groups: "groups",
  legalEntities: "legalEntities",
  access: "access",
  invitations: "invitations",
  settings: "organizationSettings",
} as const satisfies Record<OrganizationSection, string>;

/**
 * The line on what the section holds, for the sections whose purpose the
 * menu label alone does not tell.
 */
const SECTION_DESCRIPTION_KEYS: Partial<
  Record<OrganizationSection, "agents.description" | "organization.access.description">
> = {
  agents: "agents.description",
  access: "organization.access.description",
};

/**
 * One section of the organization chosen in the header, under a heading with
 * the page's name, the organization's name, and for some sections a line on
 * what the section holds.
 *
 * OWNER and ADMIN get edit controls. Agents, Groups and Access answer not found to
 * anyone else, and Legal entities to a GUEST, because the API lets only those
 * roles read them. The role comes from the current-user ("me") query: while it
 * loads the skeleton shows, and when no user could be loaded an error with a
 * retry replaces the section, so "role unknown" never renders as "read-only".
 * A failed background refetch keeps the cached user, whose role is still known.
 *
 * The page's server component prefetches the organization and the section's
 * lists, so the first paint needs no client request.
 */
export function CurrentOrganizationPage({ section }: { section: OrganizationSection }) {
  const t = useTranslations();
  const organizationId = useOrganizationId();
  const {
    data: meResponse,
    isPending: isMePending,
    isError: isMeError,
    refetch: refetchMe,
  } = useGetMe();
  const {
    data: organizationResponse,
    isLoading: isOrganizationLoading,
    isError: isOrganizationError,
  } = useGetOrganization(organizationId);

  const currentRole = meResponse?.data?.organizations?.find(
    (organization) => organization.id === organizationId
  )?.role;
  const canEdit = currentRole === "OWNER" || currentRole === "ADMIN";
  const canReadLegalEntities = canEdit || currentRole === "MEMBER";
  const organization = organizationResponse?.data;
  const hasNoUser = isMeError && !meResponse;

  if (isOrganizationLoading || isMePending) {
    return <TableLoading label={t("organization.loadingOrganization")} />;
  }

  // Deleted, or the user lost access to it since the header chose it.
  if (!organizationId || isOrganizationError || !organization) {
    return (
      <EmptyState
        title={t("organization.notFound")}
        description={t("organization.notFoundDescription")}
      />
    );
  }

  const isHidden =
    !hasNoUser &&
    (((section === "agents" || section === "groups" || section === "access") && !canEdit) ||
      (section === "legalEntities" && !canReadLegalEntities));

  const descriptionKey = SECTION_DESCRIPTION_KEYS[section];
  const showsBody = !hasNoUser && !isHidden;

  return (
    <div className="flex flex-col gap-6">
      <PageHeading
        title={t(`sidebar.${SECTION_TITLE_KEYS[section]}`)}
        description={
          <div className="flex flex-col gap-1">
            {organization.name && <p className="text-muted-foreground">{organization.name}</p>}
            {descriptionKey && showsBody && (
              <p className="text-muted-foreground text-sm">{t(descriptionKey)}</p>
            )}
          </div>
        }
      />

      {hasNoUser ? (
        <OrganizationLoadError onRetry={() => void refetchMe()} />
      ) : isHidden ? (
        <EmptyState
          title={t("errors.notFound.title")}
          description={t("errors.notFound.description")}
        />
      ) : (
        <SectionBody
          section={section}
          organizationId={organizationId}
          canEdit={canEdit}
          currentRole={currentRole}
          organization={organization}
        />
      )}
    </div>
  );
}

function SectionBody({
  section,
  organizationId,
  canEdit,
  currentRole,
  organization,
}: {
  section: OrganizationSection;
  organizationId: string;
  canEdit: boolean;
  currentRole: OrganizationRole | undefined;
  organization: OrganizationDetail;
}) {
  switch (section) {
    case "agents":
      return <AgentsTab organizationId={organizationId} />;
    case "humans":
      return <MembersTab organizationId={organizationId} canEdit={canEdit} />;
    case "groups":
      return <GroupsTab organizationId={organizationId} />;
    case "legalEntities":
      return <LegalEntitiesTab organizationId={organizationId} canEdit={canEdit} />;
    case "access":
      return <AccessTab organizationId={organizationId} />;
    case "invitations":
      return (
        <InvitationsTab
          organizationId={organizationId}
          canEdit={canEdit}
          currentUserRole={currentRole}
        />
      );
    case "settings":
      return (
        <OrganizationDetailForm
          organizationId={organizationId}
          orgData={organization}
          canEdit={canEdit}
        />
      );
  }
}
