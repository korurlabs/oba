import { AppState, type AppStateStatus, Platform } from "react-native";
import * as WebBrowser from "expo-web-browser";

import { ENV } from "@/lib/env";

/**
 * The in-app browser for the web pages the app does not draw: the Account and
 * Admin web apps (onboarding, invitations, organizations), the Keycloak
 * account console, the Kazan web board settings and the other Korur apps.
 *
 * `openBrowserAsync` shows SFSafariViewController on iOS and a Chrome Custom
 * Tab on Android. On Android the tab shares Chrome's cookies, so a web app opens
 * signed in with the Keycloak session; on iOS the in-app browser keeps its own
 * cookies and asks for sign-in once. `openAuthSessionAsync` would share the
 * login on iOS too, but shows the system "wants to use korur.com to sign in"
 * prompt on every open.
 */

/** An Oba web app that holds pages the app opens. */
export type PlatformApp = "account" | "admin";

/**
 * Oba web pages the app opens, at the same places the Kazan web app links
 * to. Account holds what every user manages for themselves; Admin holds the
 * organizations, which only their owners and admins change. The paths carry
 * no locale prefix.
 */
export const PLATFORM_PAGES = {
  /** Accept an invitation or create the first organization. */
  onboarding: { app: "account", path: "/onboarding" },
  invitations: { app: "account", path: "/invitations" },
  newOrganization: { app: "account", path: "/organizations/new" },
  /** The user's own account: profile, settings, invitations and organizations. */
  profile: { app: "account", path: "/" },
  /** The organization list, where organizations and their members are managed. */
  organizations: { app: "admin", path: "/" },
} as const satisfies Record<string, { app: PlatformApp; path: string }>;

export type PlatformPage = keyof typeof PLATFORM_PAGES;

const PLATFORM_APP_URLS: Record<PlatformApp, string> = {
  account: ENV.accountUrl,
  admin: ENV.adminUrl,
};

/** Keycloak account console pages of realm `sensey`. */
export const KEYCLOAK_ACCOUNT_PAGES = {
  /** "Password and sign-in". */
  signingIn: "/account/account-security/signing-in",
  /** "Active sessions". */
  deviceActivity: "/account/account-security/device-activity",
} as const;

export type KeycloakAccountPage = keyof typeof KEYCLOAK_ACCOUNT_PAGES;

/** A Korur app listed under "Korur apps" on Home. */
export interface KorurApp {
  /** Stable id, also the i18n key suffix of its description. */
  id: string;
  name: string;
  url: string;
}

/**
 * The Korur apps opened from Home. A static list: the apps change with a store
 * release, not at runtime. Kazan web follows the build profile.
 */
export const KORUR_APPS: readonly KorurApp[] = [
  { id: "kagi", name: "Kagi", url: "https://kagi.pw" },
  { id: "kaya", name: "Kaya", url: "https://kaya.korur.com" },
  { id: "kazan", name: "Kazan", url: ENV.kazanWebUrl },
  { id: "kalkan", name: "Kalkan", url: "https://kalkan.korur.com" },
  { id: "betiks", name: "Betiks", url: "https://betiks.korur.com" },
];

/** The absolute URL of an Oba web page. */
export function platformPageUrl(page: PlatformPage): string {
  const { app, path } = PLATFORM_PAGES[page];
  return `${PLATFORM_APP_URLS[app]}${path}`;
}

/** The absolute URL of a Keycloak account console page. */
export function keycloakAccountUrl(page: KeycloakAccountPage): string {
  return `${ENV.keycloakIssuer}${KEYCLOAK_ACCOUNT_PAGES[page]}`;
}

/** The absolute URL of a Kazan web path such as `/boards/kazan/settings`. */
export function kazanWebUrl(path: string): string {
  return `${ENV.kazanWebUrl}${path.startsWith("/") ? path : `/${path}`}`;
}

/** A watch for the app coming back to the foreground. */
export interface ReturnWatch {
  /** Resolves the first time the app is active again after it has left the foreground. */
  returned: Promise<void>;
  /** Stops listening; `returned` then never resolves. Safe to call more than once. */
  cancel: () => void;
}

/**
 * Starts watching AppState for the app leaving the foreground and coming
 * back. On Android `openBrowserAsync` resolves as soon as the Custom Tab
 * opens, so the app learns that the tab closed when it is active again. The
 * watch starts before the tab opens: the app can already be in the background
 * when `openBrowserAsync` resolves, and a listener attached then would miss
 * that change and wait forever.
 */
export function watchReturnToApp(): ReturnWatch {
  let subscription: { remove: () => void } | null = null;
  const cancel = (): void => {
    subscription?.remove();
    subscription = null;
  };
  const returned: Promise<void> = new Promise<void>((resolve) => {
    let left = false;
    subscription = AppState.addEventListener("change", (state: AppStateStatus) => {
      if (state !== "active") {
        left = true;
        return;
      }
      if (left) {
        cancel();
        resolve();
      }
    });
  });
  return { returned, cancel };
}

/**
 * Opens `url` in the in-app browser and resolves when the user closes it, so
 * the caller can refetch what the page may have changed (organizations,
 * `GET /users/me`). A failure to open is logged and re-thrown for the caller to
 * show.
 */
export async function openInAppBrowser(url: string): Promise<void> {
  const watch: ReturnWatch | null = Platform.OS === "android" ? watchReturnToApp() : null;
  try {
    const result: WebBrowser.WebBrowserResult = await WebBrowser.openBrowserAsync(url, {
      dismissButtonStyle: "close",
      presentationStyle: WebBrowser.WebBrowserPresentationStyle.PAGE_SHEET,
      showTitle: true,
    });
    if (watch && result.type === WebBrowser.WebBrowserResultType.OPENED) {
      await watch.returned;
    }
  } catch (error: unknown) {
    console.warn(
      "[browser] opening the in-app browser failed",
      error instanceof Error ? error.message : error
    );
    throw error;
  } finally {
    watch?.cancel();
  }
}

/** Opens an Oba web page and resolves when the user closes it. */
export function openPlatformPage(page: PlatformPage): Promise<void> {
  return openInAppBrowser(platformPageUrl(page));
}

/** Opens a Keycloak account console page and resolves when the user closes it. */
export function openKeycloakAccount(page: KeycloakAccountPage): Promise<void> {
  return openInAppBrowser(keycloakAccountUrl(page));
}
