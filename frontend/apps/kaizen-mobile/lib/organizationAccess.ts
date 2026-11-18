import type { UserMe } from "@oba/shared/features/users/schema";

import { kaizenOrganizationIds } from "@/lib/kaizenAccess";

/*
 * Losing the active organization mid-session. A member can be removed from
 * an organization, or lose Kaizen in it, while the app is open; the next
 * request for it answers 403. A 403 also means a missing permission inside
 * an organization the user can still use, so the user's organizations, read
 * again, decide which one it is. No native or React import, so `bun test`
 * covers it.
 */

/** The user's organizations as `GET /users/me` reports them. */
export interface OrganizationAccess {
  /** Every organization the user belongs to. */
  memberIds: readonly string[];
  /** The organizations among them that grant Kaizen. */
  kaizenIds: readonly string[];
}

/** The access of the user `GET /users/me` returned. */
export function organizationAccessOf(me: UserMe): OrganizationAccess {
  return {
    memberIds: (me.organizations ?? []).map((organization) => organization.id),
    kaizenIds: kaizenOrganizationIds(me),
  };
}

/**
 * Why the active organization can no longer be used: `membership` when the
 * user left it or was removed, `kaizen` when the user still belongs to it but
 * it no longer grants Kaizen.
 */
export type OrganizationAccessLoss = "membership" | "kaizen";

/**
 * What the 403 says about the active organization, or null when it is not
 * about the active one or the user can still use it (a missing permission).
 */
export function organizationAccessLoss(
  forbiddenOrganizationId: string,
  activeOrganizationId: string | null,
  access: OrganizationAccess
): OrganizationAccessLoss | null {
  if (forbiddenOrganizationId !== activeOrganizationId) return null;
  if (!access.memberIds.includes(forbiddenOrganizationId)) return "membership";
  if (!access.kaizenIds.includes(forbiddenOrganizationId)) return "kaizen";
  return null;
}

/** What {@link watchOrganizationAccess} needs from the app. */
export interface OrganizationAccessWatch {
  /** Calls the listener with the organization of every organization-scoped 403. */
  subscribe: (listener: (organizationId: string) => void) => () => void;
  /** Reads the user's organizations from the server, not from a cache. */
  loadOrganizationAccess: () => Promise<OrganizationAccess>;
  /** The active organization at the time of the answer. */
  activeOrganizationId: () => string | null;
  /** The user can no longer use the active organization: clear it and say why. */
  onAccessLost: (organizationId: string, loss: OrganizationAccessLoss) => Promise<void> | void;
  /** The check itself failed; it is already logged, the user should hear of it. */
  onCheckFailed: (error: unknown) => void;
}

/**
 * Reads the user's organizations after an organization-scoped 403 and calls
 * `onAccessLost` when the active organization is gone or no longer grants
 * Kaizen. One check at a time: a screen that fails several requests at once
 * triggers one read, and its answer covers them all. Returns the unsubscribe
 * function.
 */
export function watchOrganizationAccess(watch: OrganizationAccessWatch): () => void {
  let checking: boolean = false;

  const check = async (organizationId: string): Promise<void> => {
    const access: OrganizationAccess = await watch.loadOrganizationAccess();
    const loss: OrganizationAccessLoss | null = organizationAccessLoss(
      organizationId,
      watch.activeOrganizationId(),
      access
    );
    if (loss !== null) await watch.onAccessLost(organizationId, loss);
  };

  return watch.subscribe((organizationId: string) => {
    if (checking) return;
    checking = true;
    check(organizationId)
      .catch((error: unknown) => {
        console.warn(
          "[organizationAccess] checking access to the organization failed",
          error instanceof Error ? error.message : error
        );
        watch.onCheckFailed(error);
      })
      .finally(() => {
        checking = false;
      });
  });
}
