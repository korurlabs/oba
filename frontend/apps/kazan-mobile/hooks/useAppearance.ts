import { colorScheme } from "nativewind";
import { useSyncExternalStore } from "react";
import { Platform } from "react-native";
import * as SecureStore from "expo-secure-store";

import { isAppearance, type Appearance } from "@/components/account/accountModel";

/**
 * The Appearance setting: light, dark, or the device's own scheme. It is a
 * preference of this device, not of the account, so the phone can stay dark
 * while Kazan web is light. NativeWind's `colorScheme.set` drives React
 * Native's `Appearance`, so the tokens, the navigation theme, the native tabs
 * and system dialogs all switch at once.
 */

/** SecureStore key of the Appearance choice. The value is not secret; SecureStore is the app's only device store. */
export const APPEARANCE_STORAGE_KEY = "kazan_appearance";

let current: Appearance | null = null;
const listeners = new Set<() => void>();

/**
 * The stored choice, read synchronously so it can be applied before the first
 * frame. A failed read keeps the device scheme and is logged.
 */
export function readStoredAppearance(): Appearance {
  if (Platform.OS === "web") return "system";
  try {
    const stored: string | null = SecureStore.getItem(APPEARANCE_STORAGE_KEY);
    return isAppearance(stored) ? stored : "system";
  } catch (error: unknown) {
    console.warn(
      "[appearance] reading the stored appearance failed",
      error instanceof Error ? error.message : error
    );
    return "system";
  }
}

function snapshot(): Appearance {
  if (current === null) current = readStoredAppearance();
  return current;
}

/** Applies the stored choice. Call it once at start, before the first frame. */
export function applyStoredAppearance(): void {
  colorScheme.set(snapshot());
}

/**
 * Applies `appearance` at once and keeps it for the next start. The new
 * scheme is already on screen when the write runs; a failed write is
 * re-thrown so the caller can say the choice will not survive a restart.
 */
export async function setAppearance(appearance: Appearance): Promise<void> {
  current = appearance;
  colorScheme.set(appearance);
  for (const listener of listeners) listener();
  if (Platform.OS === "web") return;
  try {
    await SecureStore.setItemAsync(APPEARANCE_STORAGE_KEY, appearance);
  } catch (error: unknown) {
    console.warn(
      "[appearance] saving the appearance failed",
      error instanceof Error ? error.message : error
    );
    throw error;
  }
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/** The current Appearance choice; re-renders when it changes. */
export function useAppearance(): Appearance {
  return useSyncExternalStore(subscribe, snapshot, snapshot);
}
