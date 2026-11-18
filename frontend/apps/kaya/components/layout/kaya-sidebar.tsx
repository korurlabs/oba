"use client";

import type { ComponentType, MouseEvent } from "react";
import {
  BookOpen,
  Boxes,
  ClipboardList,
  Cpu,
  CreditCard,
  FolderTree,
  GitPullRequest,
  Lightbulb,
  MessagesSquare,
  ScrollText,
  Settings,
  ShieldAlert,
  ShieldCheck,
  Sparkles,
} from "lucide-react";

import { matchesPath, type SkyNavItem, type SkyNavSection } from "@oba/ui/sky/app-shell";

import { mayLeavePage } from "@/features/versions/components/draft-context";

/** The menu sections: what the agents did, and what steers them. */
type NavSection = "Activity" | "Configuration";

type NavItem = {
  label: string;
  href: string;
  icon: ComponentType<{ className?: string }>;
  section?: NavSection;
  /** Further path prefixes this entry owns, such as the session pages opened from the wall. */
  alsoActiveOn?: readonly string[];
};

/** The pages in the menu, section by section; a page joins it once it reads Kaya's API. */
export const NAV_ITEMS: readonly NavItem[] = [
  { label: "Machines", href: "/machines", icon: Cpu, section: "Activity" },
  {
    label: "Sessions",
    href: "/",
    icon: MessagesSquare,
    section: "Activity",
    alsoActiveOn: ["/sessions"],
  },
  { label: "Plans", href: "/plans", icon: ClipboardList, section: "Activity" },
  { label: "Lessons", href: "/lessons", icon: Lightbulb, section: "Activity" },
  { label: "Accounts", href: "/accounts", icon: CreditCard, section: "Activity" },
  { label: "Packs", href: "/packs", icon: Boxes, section: "Configuration" },
  { label: "Skills", href: "/skills", icon: Sparkles, section: "Configuration" },
  { label: "Allowlist", href: "/allowlist", icon: ShieldCheck, section: "Configuration" },
  { label: "Rules", href: "/rules", icon: ScrollText, section: "Configuration" },
  { label: "Conventions", href: "/conventions", icon: BookOpen, section: "Configuration" },
  { label: "Guardrails", href: "/guardrails", icon: ShieldAlert, section: "Configuration" },
  { label: "Path mapping", href: "/path-mapping", icon: FolderTree, section: "Configuration" },
  { label: "Changes", href: "/changes", icon: GitPullRequest, section: "Configuration" },
];

const NAV_SECTIONS: readonly NavSection[] = ["Activity", "Configuration"];

/** Settings sits apart below the sections, as it configures the rest. */
const SETTINGS_ITEM: NavItem = { label: "Settings", href: "/settings", icon: Settings };

/**
 * A menu link leaves the page and its open editor, so it asks first while a
 * config edit is unsaved. The menu sits outside the page, so it reads the
 * page's unsaved edits instead of its editor context.
 */
export function guardNavigation(
  event: Pick<MouseEvent<HTMLAnchorElement>, "preventDefault">,
  confirm: (question: string) => boolean = (question: string) => window.confirm(question)
): void {
  if (!mayLeavePage(confirm)) event.preventDefault();
}

/** A menu entry as the shell takes it: active on its own page and on every prefix it also owns. */
function toSkyItem(item: NavItem): SkyNavItem {
  const prefixes: readonly string[] = [item.href, ...(item.alsoActiveOn ?? [])];
  return {
    label: item.label,
    href: item.href,
    icon: item.icon,
    isActive: (pathname: string) =>
      prefixes.some((prefix: string) => matchesPath(pathname, prefix)),
  };
}

/** The menu of the shell, section by section. */
export const KAYA_NAV_SECTIONS: readonly SkyNavSection[] = NAV_SECTIONS.map(
  (section: NavSection) => ({
    label: section,
    items: NAV_ITEMS.filter((item: NavItem) => item.section === section).map(toSkyItem),
  })
);

/** Settings, set apart at the foot of the menu. */
export const KAYA_SETTINGS_ITEM: SkyNavItem = toSkyItem(SETTINGS_ITEM);
