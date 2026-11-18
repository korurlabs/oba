/**
 * The organization the user works in, for the API client to send as
 * `X-Organization-ID`. The identity provider sets it from the platform user
 * before any page renders; the server checks the membership on each call, so
 * this value only chooses, it grants nothing. It lives in memory only: a
 * reload starts again from the user's default organization on the platform.
 */
let current: string | null = null;

/** The chosen organization's id, or null before the identity is known. */
export function currentOrganizationId(): string | null {
  return current;
}

export function setCurrentOrganizationId(id: string | null): void {
  current = id;
}
