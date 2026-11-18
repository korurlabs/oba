/** The parts of `useSession()` the organization depends on. */
export interface SessionOrganizationState {
  status: "authenticated" | "loading" | "unauthenticated";
  session: { user?: { defaultOrganizationId?: string | null } } | null | undefined;
}

/**
 * The organization Otak requests are sent for.
 *
 * The client reads its session only after mount, but the server render and
 * the first client render must build the same query keys the pages
 * prefetched, so until a session has been read the organization the server
 * rendered with stands in. Once read, the session decides, including while
 * it reloads after an organization switch: it is the value the API client
 * sends as `X-Organization-ID`.
 */
export function resolveOrganizationId(
  { status, session }: SessionOrganizationState,
  serverOrganizationId: string | null
): string | null {
  if (session) {
    return session.user?.defaultOrganizationId ?? null;
  }
  if (status === "loading") {
    return serverOrganizationId;
  }
  return null;
}
