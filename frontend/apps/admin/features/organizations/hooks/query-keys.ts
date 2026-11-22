/**
 * Query-key factories for the organization queries: organization detail,
 * organization members, organization invitations, groups, app access and
 * legal entities.
 *
 * This module is intentionally NOT marked "use client". Server components
 * (page.tsx prefetch) import these helpers to set cache entries under the
 * EXACT same keys the client hooks use. If they lived in the "use client"
 * hook files, importing them from a server component would turn every export
 * into a client reference proxy and the function members (e.g. `detail`)
 * would stop being callable on the server (see server-prefetch-convention and
 * `features/boards/hooks/query-keys.ts`).
 *
 * The "use client" hook files re-export these so client imports can keep
 * reading the keys from the hook they belong to.
 */
export const ORGANIZATION_KEYS = {
  all: ["organizations"] as const,
  detail: (id: string) => [...ORGANIZATION_KEYS.all, "detail", id] as const,
};

export const MEMBER_KEYS = {
  all: ["organizationMembers"] as const,
  list: (orgId: string) => [...MEMBER_KEYS.all, "list", orgId] as const,
};

export const INVITATION_KEYS = {
  all: ["invitations"] as const,
  list: (orgId: string) => [...INVITATION_KEYS.all, "list", orgId] as const,
};

/**
 * Group keys start with the organization id, so that `organization(orgId)`
 * matches the group list and every member list of that organization: a member
 * removal or a role change touches all of them at once.
 */
export const GROUP_KEYS = {
  all: ["organizationGroups"] as const,
  organization: (orgId: string) => [...GROUP_KEYS.all, orgId] as const,
  list: (orgId: string) => [...GROUP_KEYS.organization(orgId), "list"] as const,
  members: (orgId: string, groupId: string) =>
    [...GROUP_KEYS.organization(orgId), "members", groupId] as const,
};

/**
 * App access keys start with the organization id, so that
 * `organization(orgId)` matches both the grant list and the effective access
 * list of that organization.
 */
export const APP_ACCESS_KEYS = {
  all: ["appAccess"] as const,
  organization: (orgId: string) => [...APP_ACCESS_KEYS.all, orgId] as const,
  grants: (orgId: string) => [...APP_ACCESS_KEYS.organization(orgId), "grants"] as const,
  members: (orgId: string) => [...APP_ACCESS_KEYS.organization(orgId), "members"] as const,
};

/**
 * Legal entity keys start with the organization id, so that
 * `organization(orgId)` matches the list and every detail of that
 * organization.
 */
export const LEGAL_ENTITY_KEYS = {
  all: ["legalEntities"] as const,
  organization: (orgId: string) => [...LEGAL_ENTITY_KEYS.all, orgId] as const,
  list: (orgId: string) => [...LEGAL_ENTITY_KEYS.organization(orgId), "list"] as const,
  detail: (orgId: string, legalEntityId: string) =>
    [...LEGAL_ENTITY_KEYS.organization(orgId), "detail", legalEntityId] as const,
};
