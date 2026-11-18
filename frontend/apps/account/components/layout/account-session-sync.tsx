"use client";

import { useAccountSessionSync } from "@oba/shared/features/users/hooks/use-account-session-sync";

/**
 * Runs the account session side effects (pending-invitation session sync,
 * stored-theme sync) on every page of the Account shell and renders nothing.
 */
export function AccountSessionSync() {
  useAccountSessionSync();
  return null;
}
