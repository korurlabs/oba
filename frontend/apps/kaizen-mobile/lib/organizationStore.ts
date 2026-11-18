import { useSyncExternalStore } from "react";
import { Platform } from "react-native";
import * as SecureStore from "expo-secure-store";

/**
 * The active organization: the one whose boards, tickets and chat the app
 * shows, sent as `X-Organization-ID` on every request (lib/api.ts).
 *
 * The id is kept in memory, so the request interceptor reads it without an
 * await, and in SecureStore, so the choice survives a restart. Listeners let
 * React re-render on a switch (`useActiveOrganizationId`).
 */

/** SecureStore key of the active organization id. */
export const ACTIVE_ORGANIZATION_KEY = "kaizen_active_organization";

let activeOrganizationId: string | null = null;
const listeners = new Set<() => void>();

function notify(): void {
  for (const listener of listeners) {
    try {
      listener();
    } catch (error: unknown) {
      console.warn(
        "[organizationStore] listener threw",
        error instanceof Error ? error.message : error
      );
    }
  }
}

/**
 * Credentials and the organization choice live only in the iOS Keychain and
 * the Android Keystore. The web has no store a script cannot read, so a web
 * build fails here loudly instead of writing to localStorage.
 */
function assertNativeSecureStorage(operation: string): void {
  if (Platform.OS === "web") {
    throw new Error(`[organizationStore] ${operation} is not supported on web`);
  }
}

/** The active organization id, or null before one is chosen. Synchronous. */
export function getActiveOrganizationId(): string | null {
  return activeOrganizationId;
}

/** Stores and announces the active organization. */
export async function setActiveOrganizationId(organizationId: string): Promise<void> {
  assertNativeSecureStorage("setActiveOrganizationId");
  activeOrganizationId = organizationId;
  notify();
  await SecureStore.setItemAsync(ACTIVE_ORGANIZATION_KEY, organizationId);
}

/** Forgets the active organization, in memory and on the device. */
export async function clearActiveOrganizationId(): Promise<void> {
  assertNativeSecureStorage("clearActiveOrganizationId");
  activeOrganizationId = null;
  notify();
  await SecureStore.deleteItemAsync(ACTIVE_ORGANIZATION_KEY);
}

/** Loads the stored id into memory at start and returns it. */
export async function hydrateActiveOrganizationId(): Promise<string | null> {
  assertNativeSecureStorage("hydrateActiveOrganizationId");
  activeOrganizationId = await SecureStore.getItemAsync(ACTIVE_ORGANIZATION_KEY);
  notify();
  return activeOrganizationId;
}

/** Calls `listener` after every change. Returns the unsubscribe function. */
export function subscribeActiveOrganization(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/** The active organization id, re-rendering the component on a switch. */
export function useActiveOrganizationId(): string | null {
  return useSyncExternalStore(subscribeActiveOrganization, getActiveOrganizationId);
}

/** Inputs of {@link pickActiveOrganizationId}. */
export interface ActiveOrganizationCandidates {
  /** Ids of the organizations the user belongs to (`GET /organizations`). */
  memberOrganizationIds: readonly string[];
  /** The id stored on this device, if any. */
  storedOrganizationId: string | null;
  /** `defaultOrganizationId` of `GET /users/me`, if any. */
  defaultOrganizationId: string | null;
}

/**
 * The organization to make active: the stored one while the user still belongs
 * to it, else the user's default organization, else the first one. Null when
 * the user belongs to none, which is the onboarding case. Used at sign-in, at
 * start, and when access to the active organization is lost.
 */
export function pickActiveOrganizationId(candidates: ActiveOrganizationCandidates): string | null {
  const { memberOrganizationIds, storedOrganizationId, defaultOrganizationId } = candidates;
  if (storedOrganizationId !== null && memberOrganizationIds.includes(storedOrganizationId)) {
    return storedOrganizationId;
  }
  if (defaultOrganizationId !== null && memberOrganizationIds.includes(defaultOrganizationId)) {
    return defaultOrganizationId;
  }
  return memberOrganizationIds[0] ?? null;
}
