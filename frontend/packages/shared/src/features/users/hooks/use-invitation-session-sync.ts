"use client";

import { useSession } from "next-auth/react";
import { useEffect, useRef } from "react";
import type { UserMe } from "../schema";

/**
 * Keeps the session's pendingInvitationIds in sync with the API response.
 *
 * When a new invitation arrives (or an existing one is resolved) while the user
 * is already logged in, the JWT token won't know about it until the next
 * session.update() call. This hook detects mismatches between the API's
 * pendingInvitations and the session's pendingInvitationIds, then triggers
 * update({}) to refresh the token via the JWT callback.
 *
 * Must be called from a component that already fetches useGetMe (to avoid
 * extra API calls). The caller passes the user data directly.
 */
export function useInvitationSessionSync(meData: UserMe | undefined) {
  const { data: session, update } = useSession();
  const isUpdatingRef = useRef(false);

  const apiPendingIds =
    meData?.pendingInvitations
      ?.filter((inv) => inv.status === "PENDING")
      .map((inv) => inv.id) ?? [];

  const sessionPendingIds = session?.pendingInvitationIds ?? [];

  useEffect(() => {
    // Don't sync until we have both data sources
    if (!meData || !session) return;

    // Prevent concurrent update() calls
    if (isUpdatingRef.current) return;

    const hasNewInvitations = apiPendingIds.some(
      (id) => !sessionPendingIds.includes(id),
    );
    const hasRemovedInvitations = sessionPendingIds.some(
      (id) => !apiPendingIds.includes(id),
    );

    if (hasNewInvitations || hasRemovedInvitations) {
      isUpdatingRef.current = true;
      // The empty object is required: next-auth sends a POST only when
      // update() gets an argument, and only a POST runs the JWT callback's
      // "update" branch that re-reads the pending invitations. A bare update()
      // is a GET that returns the token unchanged.
      update({})
        .catch((error: unknown) => {
          // Not surfaced to the user: nothing they did failed, and the next
          // change of either id list runs this sync again.
          console.error("[useInvitationSessionSync] session update failed:", error);
        })
        .finally(() => {
          isUpdatingRef.current = false;
        });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [JSON.stringify(apiPendingIds), JSON.stringify(sessionPendingIds)]);
}
