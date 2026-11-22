import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@oba/shared/schema";
import type { OrganizationMember } from "@oba/shared/features/organizations/schemas";
import type {
  PageCreateRequest,
  PageDetail,
  PageListItem,
  PageMoveRequest,
  PagePermission,
  PagePermissionRequest,
  PageRecentItem,
  PageRevisionDetail,
  PageRevisionListItem,
  PageTrashItem,
  PageUpdateRequest,
} from "../schemas";

// ── Pages ───────────────────────────────────────────────────────────

/**
 * Every live page under a root the caller holds a level on, flat, parents
 * before children; roots carry the caller's level.
 */
export async function listPages() {
  const res = await api.get<ApiResponse<PageListItem[]>>(API_ROUTES.pages.list);
  return res.data;
}

/** One page with its translations; a page the caller cannot see answers 404 (`KLK_NOT_001`). */
export async function getPage(pageId: string) {
  const res = await api.get<ApiResponse<PageDetail>>(API_ROUTES.pages.detail(pageId));
  return res.data;
}

/**
 * Creates a page. A root page (no parent) needs an organization ADMIN; a child
 * needs EDIT on its root. A slug taken among the siblings answers `KLK_STA_001`.
 */
export async function createPage(data: PageCreateRequest) {
  const res = await api.post<ApiResponse<PageDetail>>(API_ROUTES.pages.list, data);
  return res.data;
}

/**
 * Changes a page against the version the editor loaded; a stale version
 * answers `KLK_STA_002`. Needs EDIT on the root.
 */
export async function updatePage(pageId: string, data: PageUpdateRequest) {
  const res = await api.put<ApiResponse<PageDetail>>(API_ROUTES.pages.detail(pageId), data);
  return res.data;
}

/** Moves a page to a new parent and position; an invalid target answers `KLK_VAL_004`. */
export async function movePage(pageId: string, data: PageMoveRequest) {
  const res = await api.patch<ApiResponse<PageDetail>>(API_ROUTES.pages.position(pageId), data);
  return res.data;
}

/** Moves a page and its subtree to the trash. Needs EDIT on the root, MANAGE for a root page. */
export async function deletePage(pageId: string): Promise<void> {
  await api.delete(API_ROUTES.pages.detail(pageId));
}

/**
 * Brings a deleted page back with the pages its delete took. A deleted parent
 * answers `KLK_STA_003`, a live page `KLK_VAL_008`. Needs EDIT on the root,
 * MANAGE for a root page.
 */
export async function restorePage(pageId: string) {
  const res = await api.post<ApiResponse<PageDetail>>(API_ROUTES.pages.restore(pageId));
  return res.data;
}

// ── History, trash and recent ───────────────────────────────────────

/** The revisions of a page, newest first. Needs VIEW on the page. */
export async function listPageRevisions(pageId: string) {
  const res = await api.get<ApiResponse<PageRevisionListItem[]>>(
    API_ROUTES.pages.revisions(pageId)
  );
  return res.data;
}

/** One revision of a page with its body. Needs VIEW on the page. */
export async function getPageRevision(pageId: string, revisionId: string) {
  const res = await api.get<ApiResponse<PageRevisionDetail>>(
    API_ROUTES.pages.revision(pageId, revisionId)
  );
  return res.data;
}

/**
 * The top page of each deleted subtree the caller can MANAGE, newest first.
 * The server admits a MEMBER or above.
 */
export async function listTrash() {
  const res = await api.get<ApiResponse<PageTrashItem[]>>(API_ROUTES.pages.trash);
  return res.data;
}

/** The live pages the caller can see, most recently changed first; `limit` is 1 to 100. */
export async function listRecentPages(limit: number) {
  const res = await api.get<ApiResponse<PageRecentItem[]>>(API_ROUTES.pages.recent, {
    params: { limit },
  });
  return res.data;
}

// ── Permissions ─────────────────────────────────────────────────────

/** The live grants of a root page. A child page answers `KLK_VAL_006`. Needs MANAGE. */
export async function listPagePermissions(pageId: string) {
  const res = await api.get<ApiResponse<PagePermission[]>>(API_ROUTES.pages.permissions(pageId));
  return res.data;
}

/**
 * Replaces every live grant of a root page with `grants`. A guest above VIEW
 * answers `KLK_VAL_003`, a subject outside the organization `KLK_VAL_002`.
 */
export async function replacePagePermissions(pageId: string, grants: PagePermissionRequest[]) {
  const res = await api.put<ApiResponse<PagePermission[]>>(
    API_ROUTES.pages.permissions(pageId),
    grants
  );
  return res.data;
}

// ── Organization members ────────────────────────────────────────────

/** The organization's member list; a shared endpoint, not a Kalkan one. */
const organizationMembersRoute = (organizationId: string) =>
  `/organizations/${organizationId}/members`;

/**
 * Every member of the organization, active and disabled: the people the
 * access screen can grant a level to. A GUEST may read the list.
 */
export async function listOrganizationMembers(organizationId: string) {
  const res = await api.get<ApiResponse<OrganizationMember[]>>(
    organizationMembersRoute(organizationId)
  );
  return res.data;
}
