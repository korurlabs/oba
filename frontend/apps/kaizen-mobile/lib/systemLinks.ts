/*
 * Reading the links the system hands to the app, without React, so the
 * deep-link handler stays testable.
 */

/** The app path the app opens on when a link names no screen: the Home tab. */
export const HOME_PATH = "/home" as const;

/**
 * Whether a system link names no screen: the app's own root URL
 * (`kaizen:///`, which Expo Router uses when the app starts without a link)
 * or a bare `/`. A query or fragment does not name a screen either.
 */
export function isAppRootLink(link: string): boolean {
  return /^(?:kaizen:\/\/\/?)?\/?(?:[?#].*)?$/i.test(link.trim());
}

/**
 * Whether a system link is the development client's own URL
 * (`com.korur.kaizen://expo-development-client/?url=...`). iOS hands it back
 * when the user returns to a development build from another app, and keeps it
 * as the reload URL. It names no screen, so it must not move the open one.
 * Release builds never receive it.
 */
export function isDevClientLink(link: string): boolean {
  return /^(?!https?:)[a-z][a-z0-9+.-]*:\/\/expo-development-client(?:[/?#]|$)/i.test(link.trim());
}
