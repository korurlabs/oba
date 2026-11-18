"use client";

import { useQueryClient } from "@tanstack/react-query";
import { getSession, useSession } from "next-auth/react";
import { useEffect, useRef } from "react";
import { invalidateAllOrganizationQueries } from "../hooks/use-invalidate-org-queries";

/**
 * Hook that syncs organization queries when the user switches organizations
 * in another tab/app. Works by detecting organization ID changes on window focus.
 *
 * Source of truth: the user/me API endpoint (fetched via the JWT callback's
 * getMe() call). On focus we read the shared cookie; if the org changed we
 * call update() which triggers the JWT callback -> getMe() -> session refresh.
 *
 * The explicit focus-time getSession() (rather than relying on the
 * SessionProvider's visibilitychange refetch) is deliberate: `focus` also
 * fires when alt-tabbing between two visible browser windows where
 * `visibilitychange` does not, and the next-auth BroadcastChannel is
 * per-origin, so an org switch made in another app on the shared cookie
 * domain is only caught by this fetch. Auth-state transitions (logout in another tab, etc.) are NOT
 * handled here — SessionGuard owns those, without foreground navigation.
 */
export function useOrganizationSync() {
  const queryClient = useQueryClient();
  const { data: session, update } = useSession();
  // null is the personal workspace, a real selection: a switch to or from it
  // is a change like any other.
  const previousOrgIdRef = useRef<string | null>(null);
  const isInitializedRef = useRef(false);

  const hasSession = Boolean(session);
  const currentOrgId = session?.user?.defaultOrganizationId ?? null;

  // Cross-tab/cross-app focus listener
  useEffect(() => {
    const handleFocus = async () => {
      // broadcast: false — this probe must not trigger session refetches in
      // every other same-origin tab on each window focus.
      const currentSession = await getSession({ broadcast: false });

      if (!currentSession || !isInitializedRef.current) return;
      const cookieOrgId = currentSession.user?.defaultOrganizationId ?? null;
      if (cookieOrgId !== previousOrgIdRef.current) {
        try {
          await update();
        } catch (error) {
          // Session will self-correct on the next interaction (the JWT
          // callback refreshes on every session read), so no user-facing
          // surface — but log it so repeated sync failures are visible.
          console.warn("[OrganizationSync] session update after org switch failed:", error);
        }
      }
    };

    window.addEventListener("focus", handleFocus);
    return () => window.removeEventListener("focus", handleFocus);
  }, [update]);

  // Invalidate all org-scoped queries when the session org actually changes
  useEffect(() => {
    if (!hasSession) return;

    if (!isInitializedRef.current) {
      previousOrgIdRef.current = currentOrgId;
      isInitializedRef.current = true;
      return;
    }

    if (currentOrgId !== previousOrgIdRef.current) {
      previousOrgIdRef.current = currentOrgId;
      invalidateAllOrganizationQueries(queryClient);
    }
  }, [hasSession, currentOrgId, queryClient]);
}

/**
 * Provider component that enables organization sync across tabs/apps.
 * Must be placed inside both SessionProvider and QueryClientProvider.
 */
export function OrganizationSyncProvider({ children }: { children: React.ReactNode }) {
  useOrganizationSync();
  return <>{children}</>;
}
