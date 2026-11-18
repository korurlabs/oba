"use client";

import { createContext, useContext } from "react";
import { useSession } from "next-auth/react";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import {
  kazanAccessOf,
  NO_ORGANIZATION_KAZAN_ACCESS,
  OPEN_KAZAN_ACCESS,
  PENDING_KAZAN_ACCESS,
  type KazanAccess,
} from "./kazan-access";

export { isKazanShut, type KazanAccess } from "./kazan-access";

/** The answer the dashboard layout computed on the server for this page load. */
const ServerKazanAccessContext = createContext<KazanAccess>(PENDING_KAZAN_ACCESS);

interface KazanAccessProviderProps {
  /** `getServerKazanAccess()` of the request that rendered the layout. */
  serverAccess: KazanAccess;
  children: React.ReactNode;
}

/**
 * Hands the server's answer to `useKazanAccess`. The session is not part of
 * the server HTML, so `useSession` is loading on the first render; the
 * server's answer covers that moment. Without it, every Kazan query would
 * wait for the session request, and the panel would not be in the HTML.
 */
export function KazanAccessProvider({ serverAccess, children }: KazanAccessProviderProps) {
  return <ServerKazanAccessContext value={serverAccess}>{children}</ServerKazanAccessContext>;
}

/**
 * The one answer to "is Kazan open in the selected organization", read by
 * the access gate and by every Kazan query that runs on all dashboard pages.
 * The API refuses each Kazan call without an organization and in an
 * organization that does not grant the app, so those queries wait for `open`
 * instead of collecting refusals behind the gate.
 *
 * The selected organization is the session default, the one the API client
 * sends as `X-Organization-ID`. Without one the answer is `noOrganization`
 * (the personal workspace). Kazan is open when the API is left to decide:
 * the user could not be loaded, the session has no token to load it with, or
 * the selected organization is not among the user's memberships (a stale
 * default). The
 * last good user is kept through a failed refetch, so a failed refetch does
 * not open or close Kazan.
 *
 * While the session loads, the answer is the one the dashboard layout
 * computed on the server (`KazanAccessProvider`). The layout also hydrates
 * the user, so once the session is there the answer is known at once.
 */
export function useKazanAccess(): KazanAccess {
  const serverAccess = useContext(ServerKazanAccessContext);
  const { data: session, status: sessionStatus } = useSession();
  const { data: meResponse, isError: meFailed } = useGetMe();

  if (!session) return sessionStatus === "loading" ? serverAccess : OPEN_KAZAN_ACCESS;

  const selectedOrganizationId = session.user?.defaultOrganizationId;
  if (!selectedOrganizationId) return NO_ORGANIZATION_KAZAN_ACCESS;

  const me = meResponse?.data;
  if (!me) return meFailed || !session.accessToken ? OPEN_KAZAN_ACCESS : PENDING_KAZAN_ACCESS;

  return kazanAccessOf(selectedOrganizationId, me);
}

/** True when Kazan calls may go out: the `enabled` guard of every query that runs on all dashboard pages. */
export function useKazanOpen(): boolean {
  return useKazanAccess().status === "open";
}
