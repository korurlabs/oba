/**
 * API paths, relative to the API origin of the run-time config (`API_BASE_URL`).
 * Kaya's own endpoints sit under `/kaya`; the user, the organizations and their
 * members belong to the platform and keep its paths.
 */
export const API_ROUTES = {
  users: {
    me: "/users/me",
  },
  organizations: {
    members: (organizationId: string) =>
      `/organizations/${encodeURIComponent(organizationId)}/members`,
  },
  sessions: {
    list: "/kaya/sessions",
    filters: "/kaya/sessions/filters",
    detail: (sessionId: string) => `/kaya/sessions/${encodeURIComponent(sessionId)}`,
    events: (sessionId: string) => `/kaya/sessions/${encodeURIComponent(sessionId)}/events`,
    handoffs: (sessionId: string) => `/kaya/sessions/${encodeURIComponent(sessionId)}/handoffs`,
    links: (sessionId: string) => `/kaya/sessions/${encodeURIComponent(sessionId)}/links`,
    link: (sessionId: string, linkId: string) =>
      `/kaya/sessions/${encodeURIComponent(sessionId)}/links/${encodeURIComponent(linkId)}`,
  },
  plans: {
    list: "/kaya/plans",
    filters: "/kaya/plans/filters",
    detail: (planId: string) => `/kaya/plans/${encodeURIComponent(planId)}`,
    version: (planId: string, number: number) =>
      `/kaya/plans/${encodeURIComponent(planId)}/versions/${number}`,
    diff: (planId: string, number: number) =>
      `/kaya/plans/${encodeURIComponent(planId)}/versions/${number}/diff`,
    feedback: (planId: string) => `/kaya/plans/${encodeURIComponent(planId)}/feedback`,
    feedbackItem: (planId: string, feedbackId: string) =>
      `/kaya/plans/${encodeURIComponent(planId)}/feedback/${encodeURIComponent(feedbackId)}`,
  },
  lessons: {
    list: "/kaya/lessons",
    filters: "/kaya/lessons/filters",
    detail: (lessonId: string) => `/kaya/lessons/${encodeURIComponent(lessonId)}`,
  },
  stream: "/kaya/stream",
  repositories: {
    list: "/kaya/repositories",
    detail: (id: string) => `/kaya/repositories/${encodeURIComponent(id)}`,
  },
  accounts: {
    list: "/kaya/accounts",
    detail: (id: string) => `/kaya/accounts/${encodeURIComponent(id)}`,
  },
  machines: {
    list: "/kaya/machines",
    detail: (id: string) => `/kaya/machines/${encodeURIComponent(id)}`,
    token: (id: string) => `/kaya/machines/${encodeURIComponent(id)}/token`,
  },
  packs: {
    list: "/kaya/packs",
    detail: (packId: string) => `/kaya/packs/${encodeURIComponent(packId)}`,
    effective: (packId: string) => `/kaya/packs/${encodeURIComponent(packId)}/effective`,
  },
  repositoryPacks: {
    list: (repositoryId: string) => `/kaya/repositories/${encodeURIComponent(repositoryId)}/packs`,
  },
  releases: {
    list: (repositoryId: string) => `/kaya/repositories/${encodeURIComponent(repositoryId)}/releases`,
    detail: (repositoryId: string, number: number) =>
      `/kaya/repositories/${encodeURIComponent(repositoryId)}/releases/${number}`,
  },
  versions: {
    list: "/kaya/versions",
    detail: (id: string) => `/kaya/versions/${encodeURIComponent(id)}`,
    file: (id: string) => `/kaya/versions/${encodeURIComponent(id)}/file`,
    diff: (id: string) => `/kaya/versions/${encodeURIComponent(id)}/diff`,
    restore: (id: string) => `/kaya/versions/${encodeURIComponent(id)}/restore`,
  },
  drafts: {
    create: "/kaya/drafts",
    detail: (id: string) => `/kaya/drafts/${encodeURIComponent(id)}`,
    files: (id: string) => `/kaya/drafts/${encodeURIComponent(id)}/files`,
    rebase: (id: string) => `/kaya/drafts/${encodeURIComponent(id)}/rebase`,
    overrides: (id: string) => `/kaya/drafts/${encodeURIComponent(id)}/overrides`,
  },
  changes: {
    list: "/kaya/changes",
    detail: (versionId: string) => `/kaya/changes/${encodeURIComponent(versionId)}`,
    submit: (versionId: string) => `/kaya/changes/${encodeURIComponent(versionId)}/submit`,
    reviews: (versionId: string) => `/kaya/changes/${encodeURIComponent(versionId)}/reviews`,
    history: "/kaya/changes/history",
  },
  links: {
    list: "/kaya/links",
    dependencies: "/kaya/links/dependencies",
  },
  rules: {
    list: "/kaya/rules",
  },
  agentsFiles: {
    list: "/kaya/agents-files",
  },
  roles: {
    list: "/kaya/roles",
  },
  subagents: {
    list: "/kaya/subagents",
  },
  workflows: {
    list: "/kaya/workflows",
    detail: (id: string) => `/kaya/workflows/${encodeURIComponent(id)}`,
  },
  templates: {
    list: "/kaya/templates",
  },
  hooks: {
    list: "/kaya/hooks",
  },
  conventions: {
    list: "/kaya/conventions",
  },
  skills: {
    list: "/kaya/skills",
  },
  settings: {
    list: "/kaya/settings",
  },
  pathMappings: {
    list: "/kaya/path-mappings",
    tools: "/kaya/path-mappings/tools",
  },
} as const;
