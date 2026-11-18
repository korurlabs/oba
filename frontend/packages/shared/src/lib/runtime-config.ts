import type { ObaAppId } from "../constants/apps";

/**
 * The public URLs of one Oba deployment. They are read from the server
 * environment when a request is served, never inlined at build time, so one
 * image of an app runs in any deployment.
 */
export interface RuntimeConfig {
  /** Origin of the Oba API (`API_BASE_URL`); undefined sends API calls to the page's own origin. */
  apiBaseUrl: string | undefined;
  /** Public origin of the app serving the page (`BASE_URL`). */
  baseUrl: string | undefined;
  /** Origin of every Oba web app, without a trailing slash. */
  appUrls: Record<ObaAppId, string>;
}

/** Where an app runs when its URL is not configured: its local dev server, or its Korur host. */
const APP_DEFAULTS: Record<ObaAppId, { devPort: number; host: string }> = {
  kazan: { devPort: 3002, host: "kazan.korur.com" },
  kaya: { devPort: 3014, host: "kaya.korur.com" },
  kalkan: { devPort: 3019, host: "kalkan.korur.com" },
  otak: { devPort: 3018, host: "otak.korur.com" },
  admin: { devPort: 3016, host: "admin.korur.com" },
  account: { devPort: 3017, host: "account.korur.com" },
};

/** An empty value counts as unset, because a deployment may declare a key without a value. */
function configuredValue(value: string | undefined): string | undefined {
  return value || undefined;
}

function appUrl(configured: string | undefined, id: ObaAppId): string {
  const { devPort, host } = APP_DEFAULTS[id];
  const fallback =
    process.env.NODE_ENV === "development" ? `http://localhost:${devPort}` : `https://${host}`;
  return (configuredValue(configured) ?? fallback).replace(/\/+$/, "");
}

/**
 * Reads the configuration from the server environment, on every call, so a
 * changed value takes effect when the server restarts, without a rebuild.
 * Server only: the browser has no such environment and reads the copy that
 * `RuntimeConfigProvider` received from the root layout.
 */
export function readRuntimeConfig(): RuntimeConfig {
  return {
    apiBaseUrl: configuredValue(process.env.API_BASE_URL),
    baseUrl: configuredValue(process.env.BASE_URL),
    appUrls: {
      kazan: appUrl(process.env.KAZAN_URL, "kazan"),
      kaya: appUrl(process.env.KAYA_URL, "kaya"),
      kalkan: appUrl(process.env.KALKAN_URL, "kalkan"),
      otak: appUrl(process.env.OTAK_URL, "otak"),
      admin: appUrl(process.env.ADMIN_URL, "admin"),
      account: appUrl(process.env.ACCOUNT_URL, "account"),
    },
  };
}

let browserConfig: RuntimeConfig | undefined;

/** Called by `RuntimeConfigProvider` in the browser, before anything below it renders. */
export function setBrowserRuntimeConfig(config: RuntimeConfig): void {
  browserConfig = config;
}

/**
 * The configuration for code outside React, such as the Axios clients and the
 * link constants: the server environment on the server, the provider's copy in
 * the browser. A browser read before the provider has rendered is a wiring
 * error of the app, so it throws instead of guessing a URL.
 */
export function getRuntimeConfig(): RuntimeConfig {
  if (typeof window === "undefined") {
    return readRuntimeConfig();
  }
  if (!browserConfig) {
    throw new Error(
      "[runtime-config] read in the browser before RuntimeConfigProvider rendered; the root layout must wrap the app in it",
    );
  }
  return browserConfig;
}
