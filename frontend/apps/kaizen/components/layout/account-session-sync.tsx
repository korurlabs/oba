"use client";

import { useAccountSessionSync } from "@oba/shared/features/users/hooks/use-account-session-sync";

/**
 * Runs the account session side effects (pending-invitation session sync,
 * stored-theme sync) for every dashboard page and renders nothing.
 *
 * It is mounted by the dashboard layout, next to the sidebar and not inside
 * it: below 768px the sidebar is an off-canvas sheet whose content, the
 * account menu included, is unmounted while the sheet is closed.
 */
export function AccountSessionSync() {
  useAccountSessionSync();
  return null;
}
