/** Platform endpoints Ulak calls; the current user (`/users/me`) lives in `@oba/shared`. */
export const API_ROUTES = {
  organizations: {
    detail: (id: string) => `/organizations/${id}`,
  },
  organizationMembers: {
    list: (orgId: string) => `/organizations/${orgId}/members`,
    detail: (orgId: string, userId: string) =>
      `/organizations/${orgId}/members/${userId}`,
    role: (orgId: string, userId: string) =>
      `/organizations/${orgId}/members/${userId}/role`,
    status: (orgId: string, userId: string) =>
      `/organizations/${orgId}/members/${userId}/status`,
  },
  organizationGroups: {
    list: (orgId: string) => `/organizations/${orgId}/groups`,
    detail: (orgId: string, groupId: string) =>
      `/organizations/${orgId}/groups/${groupId}`,
    members: (orgId: string, groupId: string) =>
      `/organizations/${orgId}/groups/${groupId}/members`,
    member: (orgId: string, groupId: string, userId: string) =>
      `/organizations/${orgId}/groups/${groupId}/members/${userId}`,
  },
  appGrants: {
    list: (orgId: string) => `/organizations/${orgId}/app-grants`,
    detail: (orgId: string, grantId: string) =>
      `/organizations/${orgId}/app-grants/${grantId}`,
    appAccess: (orgId: string) =>
      `/organizations/${orgId}/app-grants/app-access`,
  },
  legalEntities: {
    list: "/legal-entities",
    detail: (legalEntityId: string) => `/legal-entities/${legalEntityId}`,
  },
  invitations: {
    list: "/invitations",
    detail: (invitationId: string) => `/invitations/${invitationId}`,
  },
} as const;
