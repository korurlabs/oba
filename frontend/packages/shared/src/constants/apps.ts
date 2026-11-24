import type { LucideIcon } from "lucide-react";
import { Bot, Building2, Handshake, ShieldCheck, Ticket } from "lucide-react";
import type { GrantedApp } from "../features/users/schema";
import { getRuntimeConfig } from "../lib/runtime-config";

/** Identifier of an Oba web app; also its key under `apps` in the shared locales. */
export type ObaAppId = "kazan" | "kaya" | "kalkan" | "otak" | "ulak" | "account";

export interface ObaApp {
  id: ObaAppId;
  /** Absolute origin of the app, without a trailing slash. */
  url: string;
  icon: LucideIcon;
}

/**
 * Origin of an app in this deployment, read at run time (see `getRuntimeConfig`).
 * In the browser it is available once `RuntimeConfigProvider` has rendered, so
 * call it while rendering or later, never at module load.
 */
export function appUrl(id: ObaAppId): string {
  return getRuntimeConfig().appUrls[id];
}

/**
 * Ulak paths other apps link to. Ulak holds the screens of organization owners and admins.
 * Each link is a getter, resolved on read against the run-time origin, so it
 * must be read while rendering or later, never at module load.
 */
export const ULAK_LINKS = {
  get organizations(): string {
    return appUrl("ulak");
  },
};

/**
 * Account paths other apps link to. Account holds the pages every user manages for themselves.
 * Getters, like `ULAK_LINKS`, for the same reason.
 */
export const ACCOUNT_LINKS = {
  get profile(): string {
    return appUrl("account");
  },
  get settings(): string {
    return `${appUrl("account")}/settings`;
  },
  get invitations(): string {
    return `${appUrl("account")}/invitations`;
  },
  get newOrganization(): string {
    return `${appUrl("account")}/organizations/new`;
  },
  get onboarding(): string {
    return `${appUrl("account")}/onboarding`;
  },
};

/**
 * The account onboarding page, returning the user to `callbackUrl` afterwards.
 * Account serves another origin, so a path is made absolute against the
 * calling app's own base URL; an absolute `callbackUrl` is kept as it is.
 */
export function accountOnboardingUrl(callbackUrl: string, appBaseUrl: string): string {
  const url = new URL(ACCOUNT_LINKS.onboarding);
  url.searchParams.set("callbackUrl", new URL(callbackUrl, appBaseUrl).href);
  return url.href;
}

/**
 * The Oba web apps the app switcher lists, in catalogue order. Account is not
 * one of them: it holds the user's own pages, which the profile menu opens.
 */
export const OBA_APP_IDS = [
  "kazan",
  "kaya",
  "kalkan",
  "otak",
  "ulak",
] as const satisfies readonly ObaAppId[];

const APP_ICONS: Record<(typeof OBA_APP_IDS)[number], LucideIcon> = {
  kazan: Ticket,
  kaya: Bot,
  kalkan: ShieldCheck,
  otak: Handshake,
  ulak: Building2,
};

/** The apps of the app switcher with their origins in this deployment, in catalogue order. */
export function obaApps(appUrls: Record<ObaAppId, string>): ObaApp[] {
  return OBA_APP_IDS.map((id) => ({ id, url: appUrls[id], icon: APP_ICONS[id] }));
}

/**
 * The grant each web app needs, by app id. Ulak and Account have no entry:
 * Ulak is gated by the organization role and Account is open to every user.
 */
export const GRANTED_APP_OF: Partial<Record<ObaAppId, GrantedApp>> = {
  kazan: "KAZAN",
  kaya: "KAYA",
  kalkan: "KALKAN",
  otak: "OTAK",
};
