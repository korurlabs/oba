/**
 * Backend route constants for the storage/attachments endpoints.
 *
 * These routes are owned by the cross-cutting `/storage` backend module,
 * not by any single app, which is why they live here in `@oba/shared`
 * rather than under any individual app's `lib/api-routes.ts`.
 */
export const ATTACHMENT_ROUTES = {
  upload: "/storage/attachments",
  list: "/storage/attachments",
  delete: (id: string) => `/storage/attachments/${id}`,
  /**
   * Pre-flight duplicate-check: computes a SHA-256 hash of the candidate
   * file server-side and returns `{ exists: boolean }` indicating whether an
   * attachment with the same content already exists for the given entity
   * type within the organization scope. Lets an upload flow skip redundant
   * server-side processing (e.g. a wasted AI extraction call) on a known
   * duplicate before committing the file.
   */
  exists: "/storage/attachments/exists",
} as const;
