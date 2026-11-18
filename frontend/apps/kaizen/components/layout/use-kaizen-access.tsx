"use client";

import { createContext, useContext } from "react";
import { useSession } from "next-auth/react";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import {
  kaizenAccessOf,
  NO_ORGANIZATION_KAIZEN_ACCESS,
  OPEN_KAIZEN_ACCESS,
  PENDING_KAIZEN_ACCESS,
  type KaizenAccess,
} from "./kaizen-access";

export { isKaizenShut, type KaizenAccess } from "./kaizen-access";

/** The answer the dashboard layout computed on the server for this page load. */
const ServerKaizenAccessContext = createContext<KaizenAccess>(PENDING_KAIZEN_ACCESS);

interface KaizenAccessProviderProps {
  /** `getServerKaizenAccess()` of the request that rendered the layout. */
  serverAccess: KaizenAccess;
  children: React.ReactNode;
}

/**
 * Hands the server's answer to `useKaizenAccess`. The session is not part of
 * the server HTML, so `useSession` is loading on the first render; the
 * server's answer covers that moment. Without it, every Kaizen query would
 * wait for the session request, and the panel would not be in the HTML.
 */
export function KaizenAccessProvider({ serverAccess, children }: KaizenAccessProviderProps) {
  return <ServerKaizenAccessContext value={serverAccess}>{children}</ServerKaizenAccessContext>;
}

/**
 * The one answer to "is Kaizen open in the selected organization", read by
 * the access gate and by every Kaizen query that runs on all dashboard pages.
 * The API refuses each Kaizen call without an organization and in an
 * organization that does not grant the app, so those queries wait for `open`
 * instead of collecting refusals behind the gate.
 *
 * The selected organization is the session default, the one the API client
 * sends as `X-Organization-ID`. Without one the answer is `noOrganization`
 * (the personal workspace). Kaizen is open when the API is left to decide:
 * the user could not be loaded, the session has no token to load it with, or
 * the selected organization is not among the user's memberships (a stale
 * default). The
 * last good user is kept through a failed refetch, so a failed refetch does
 * not open or close Kaizen.
 *
 * While the session loads, the answer is the one the dashboard layout
 * computed on the server (`KaizenAccessProvider`). The layout also hydrates
 * the user, so once the session is there the answer is known at once.
 */
export function useKaizenAccess(): KaizenAccess {
  const serverAccess = useContext(ServerKaizenAccessContext);
  const { data: session, status: sessionStatus } = useSession();
  const { data: meResponse, isError: meFailed } = useGetMe();

  if (!session) return sessionStatus === "loading" ? serverAccess : OPEN_KAIZEN_ACCESS;

  const selectedOrganizationId = session.user?.defaultOrganizationId;
  if (!selectedOrganizationId) return NO_ORGANIZATION_KAIZEN_ACCESS;

  const me = meResponse?.data;
  if (!me) return meFailed || !session.accessToken ? OPEN_KAIZEN_ACCESS : PENDING_KAIZEN_ACCESS;

  return kaizenAccessOf(selectedOrganizationId, me);
}

/** True when Kaizen calls may go out: the `enabled` guard of every query that runs on all dashboard pages. */
export function useKaizenOpen(): boolean {
  return useKaizenAccess().status === "open";
}
