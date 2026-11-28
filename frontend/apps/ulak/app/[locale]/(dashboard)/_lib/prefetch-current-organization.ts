import type { QueryClient, QueryKey } from "@tanstack/react-query";
import { auth } from "@oba/shared/libs/auth";
import {
  getOrganization,
  listAppGrants,
  listInvitations,
  listLegalEntities,
  listMemberAppAccess,
  listOrganizationGroups,
  listOrganizationMembers,
} from "@/features/organizations/api";
import {
  APP_ACCESS_KEYS,
  GROUP_KEYS,
  INVITATION_KEYS,
  LEGAL_ENTITY_KEYS,
  MEMBER_KEYS,
  ORGANIZATION_KEYS,
} from "@/features/organizations/hooks/query-keys";
import { listAgents } from "@/features/agents/api";
import { AGENT_KEYS } from "@/features/agents/hooks/query-keys";
import { listAzureConnections } from "@/features/integrations/api";
import { AZURE_CONNECTION_KEYS } from "@/features/integrations/hooks/query-keys";
import type { OrganizationSection } from "@/features/organizations/components/current-organization-page";

interface OrganizationQuery {
  queryKey: QueryKey;
  queryFn: () => Promise<unknown>;
}

/** The lists each section's view queries on mount, besides the organization. */
function sectionQueries(organizationId: string, section: OrganizationSection): OrganizationQuery[] {
  switch (section) {
    case "agents":
      return [
        {
          queryKey: AGENT_KEYS.list(organizationId),
          queryFn: () => listAgents(organizationId),
        },
      ];
    case "humans":
      return [
        {
          queryKey: MEMBER_KEYS.list(organizationId),
          queryFn: () => listOrganizationMembers(organizationId),
        },
      ];
    case "groups":
      return [
        {
          queryKey: GROUP_KEYS.list(organizationId),
          queryFn: () => listOrganizationGroups(organizationId),
        },
      ];
    case "legalEntities":
      return [
        {
          queryKey: LEGAL_ENTITY_KEYS.list(organizationId),
          queryFn: () => listLegalEntities(organizationId),
        },
      ];
    case "access":
      return [
        {
          queryKey: APP_ACCESS_KEYS.grants(organizationId),
          queryFn: () => listAppGrants(organizationId),
        },
        {
          queryKey: APP_ACCESS_KEYS.members(organizationId),
          queryFn: () => listMemberAppAccess(organizationId),
        },
      ];
    case "invitations":
      return [
        {
          queryKey: INVITATION_KEYS.list(organizationId),
          queryFn: () => listInvitations(organizationId),
        },
      ];
    // The provider index shows each provider's connection count, so both
    // integration pages read the same list.
    case "integrations":
    case "azureIntegration":
      return [
        {
          queryKey: AZURE_CONNECTION_KEYS.list(organizationId),
          queryFn: () => listAzureConnections(organizationId),
        },
      ];
    case "settings":
      return [];
  }
}

/**
 * Best-effort prefetch of what a section page of the current organization
 * reads on mount: the organization and the section's lists, under the keys of
 * the client hooks, for the session's default organization (the one the shell
 * and the API client use). Nothing is fetched without a default organization,
 * as the hooks wait for one. A refused or failed request is logged and left
 * out of the cache: the client then fetches it, and the organization gate and
 * the page's role rules decide what the user sees.
 */
export async function prefetchCurrentOrganization(
  queryClient: QueryClient,
  section: OrganizationSection
): Promise<void> {
  const organizationId = (await auth())?.user?.defaultOrganizationId;
  if (!organizationId) return;

  const queries: OrganizationQuery[] = [
    {
      queryKey: ORGANIZATION_KEYS.detail(organizationId),
      queryFn: () => getOrganization(organizationId),
    },
    ...sectionQueries(organizationId, section),
  ];

  await Promise.all(
    queries.map(async ({ queryKey, queryFn }) => {
      try {
        queryClient.setQueryData(queryKey, await queryFn());
      } catch (error) {
        console.error(
          `[${section}-page] prefetch of ${queryKey.join("/")} failed, client will refetch:`,
          error
        );
      }
    })
  );
}
