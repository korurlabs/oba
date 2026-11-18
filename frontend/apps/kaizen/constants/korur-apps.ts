import type { LucideIcon } from "lucide-react";
import { BookOpen, Bot, Briefcase, KeyRound, ShieldCheck, Ticket } from "lucide-react";

/** Identifier of a Korur app; also its subdomain and its key under `home.apps` in the locales. */
export type KorurAppId = "kagi" | "kaya" | "kaizen" | "kalkan" | "otag" | "betiks";

export interface KorurApp {
  id: KorurAppId;
  icon: LucideIcon;
  /** An in-app route for Kaizen itself, an absolute URL for every other app. */
  href: string;
  /** Other apps open in a new tab, so the user keeps their place in Kaizen. */
  external: boolean;
}

/** Each app other than Kaizen runs on its own `<id>.korur.com` subdomain. */
const korurAppUrl = (id: KorurAppId): string => `https://${id}.korur.com`;

/** The Korur apps shown on the home page, in display order. */
export const KORUR_APPS: readonly KorurApp[] = [
  { id: "kagi", icon: KeyRound, href: korurAppUrl("kagi"), external: true },
  { id: "kaya", icon: Bot, href: korurAppUrl("kaya"), external: true },
  { id: "kaizen", icon: Ticket, href: "/tickets", external: false },
  { id: "kalkan", icon: ShieldCheck, href: korurAppUrl("kalkan"), external: true },
  { id: "otag", icon: Briefcase, href: korurAppUrl("otag"), external: true },
  { id: "betiks", icon: BookOpen, href: korurAppUrl("betiks"), external: true },
];
