/**
 * Endpoints Kalkan calls. The current user (`/users/me`) and the attachment
 * endpoints (`/storage/attachments`) live in `@oba/shared`.
 */
export const API_ROUTES = {
  organizationMembers: {
    list: (organizationId: string) => `/organizations/${organizationId}/members`,
  },
  pages: {
    list: "/kalkan/pages",
    detail: (pageId: string) => `/kalkan/pages/${pageId}`,
    position: (pageId: string) => `/kalkan/pages/${pageId}/position`,
    restore: (pageId: string) => `/kalkan/pages/${pageId}/restore`,
    revisions: (pageId: string) => `/kalkan/pages/${pageId}/revisions`,
    revision: (pageId: string, revisionId: string) =>
      `/kalkan/pages/${pageId}/revisions/${revisionId}`,
    permissions: (pageId: string) => `/kalkan/pages/${pageId}/permissions`,
    trash: "/kalkan/pages/trash",
    recent: "/kalkan/pages/recent",
  },
} as const;
