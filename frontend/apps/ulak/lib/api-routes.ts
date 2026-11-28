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
  agents: {
    list: (orgId: string) => `/organizations/${orgId}/agents`,
    options: (orgId: string) => `/organizations/${orgId}/agents/options`,
    detail: (orgId: string, agentId: string) =>
      `/organizations/${orgId}/agents/${agentId}`,
    status: (orgId: string, agentId: string) =>
      `/organizations/${orgId}/agents/${agentId}/status`,
    token: (orgId: string, agentId: string) =>
      `/organizations/${orgId}/agents/${agentId}/token`,
    routine: (orgId: string, agentId: string) =>
      `/organizations/${orgId}/agents/${agentId}/routine`,
  },
  legalEntities: {
    list: "/legal-entities",
    detail: (legalEntityId: string) => `/legal-entities/${legalEntityId}`,
  },
  invitations: {
    list: "/invitations",
    detail: (invitationId: string) => `/invitations/${invitationId}`,
  },
  azureConnections: {
    list: "/azure/connections",
    detail: (connectionId: string) => `/azure/connections/${connectionId}`,
    verify: (connectionId: string) => `/azure/connections/${connectionId}/verify`,
  },
} as const;
