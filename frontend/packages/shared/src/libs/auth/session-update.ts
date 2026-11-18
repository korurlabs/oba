/**
 * The default organization that a client-side `update()` call passes in its
 * `user.defaultOrganizationId`, as the token should store it.
 *
 * - `undefined`: the call does not name a default organization, so the token
 *   keeps its current one. Most `update()` calls only refresh the session.
 * - `null` or `""`: the user selected the personal workspace, so the token
 *   stores `null`. Skipping this value would keep the old organization in the
 *   session whenever the `/users/me` refresh after it fails.
 * - an organization id: the token stores it.
 */
export function clientDefaultOrganizationId(
  updateData: { user?: { defaultOrganizationId?: string | null } } | null | undefined,
  current: string | null | undefined,
): string | null | undefined {
  const passed = updateData?.user?.defaultOrganizationId;
  if (passed === undefined) return current;
  return passed || null;
}
