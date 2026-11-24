"use client";

import type { LucideIcon } from "lucide-react";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { usePathname } from "@oba/shared/libs/i18n/navigation";
import {
  SidebarMenuBadge,
  SidebarMenuButton,
  SidebarMenuItem,
} from "@oba/ui/sky/sidebar";
import { cn } from "@oba/ui/lib/utils";
import { Badge } from "@/components/ui/badge";
import { useSidebarContext } from "./sidebar-context";

/** Counts above this show as "99+". */
export const BADGE_MAX = 99;

/**
 * The height, gap and padding of the sky menu's own links, so Kazan's rows
 * line up with the Settings link in the menu's foot.
 */
export const SIDEBAR_ROW_CLASS = "h-9 gap-3 px-3";

/**
 * Centres a row's badge, "+" or chevron on the taller row; the primitives
 * place them for the default row height.
 */
export const SIDEBAR_ROW_TRAILING_CLASS = "peer-data-[size=default]/menu-button:top-2";

interface SidebarItemProps {
  href: string;
  label: string;
  icon: LucideIcon;
  /**
   * Extra path prefixes that also mark this row active, for a row that stands
   * for a group of routes its own `href` is not a prefix of (the Settings row
   * opens `/settings/columns` and covers every `/settings` route).
   */
  activePrefixes?: string[];
  /**
   * A count of new items (unread messages) shown as a red badge, "99+" above
   * 99, and as a red dot on the icon in the collapsed rail. Nothing at 0.
   */
  badge?: number;
  /** What the shown count ("7", "99+") means, for screen readers and the rail's tooltip. */
  badgeLabel?: (shownCount: string) => string;
}

/**
 * A single navigation row. Must be rendered inside a `SidebarMenu`.
 *
 * Uses the shadcn `SidebarMenuButton` so the collapsed icon rail, the active
 * state and the icon-only tooltip all come from the sidebar primitives rather
 * than being hand-rolled.
 */
export function SidebarItem({ href, label, icon: Icon, activePrefixes, badge, badgeLabel }: SidebarItemProps) {
  const pathname = usePathname();
  const { closeMobile } = useSidebarContext();

  // Compare against the path part of the href only: the tickets row carries a
  // query string. Active on the route itself and on anything nested under it;
  // the slash keeps a row dark on a path that merely starts with the same
  // letters.
  const hrefPath = href.split("?")[0] ?? href;
  const isActive =
    pathname === hrefPath ||
    pathname.startsWith(`${hrefPath}/`) ||
    (activePrefixes?.some((prefix) => pathname.startsWith(prefix)) ?? false);

  const count = badge ?? 0;
  const shownCount = count > BADGE_MAX ? `${BADGE_MAX}+` : String(count);
  const countLabel = count > 0 ? (badgeLabel?.(shownCount) ?? shownCount) : undefined;

  return (
    <SidebarMenuItem>
      <SidebarMenuButton
        asChild
        isActive={isActive}
        tooltip={countLabel ? `${label} (${countLabel})` : label}
        className={cn(SIDEBAR_ROW_CLASS, count > 0 && "pr-9")}
      >
        <Link href={href} onClick={closeMobile}>
          <Icon />
          <span>
            {label}
            {countLabel && <span className="sr-only">, {countLabel}</span>}
          </span>
        </Link>
      </SidebarMenuButton>
      {count > 0 && (
        <>
          <SidebarMenuBadge aria-hidden className={SIDEBAR_ROW_TRAILING_CLASS}>
            <Badge variant="destructive" className="h-5 min-w-5 justify-center rounded-full px-1.5 py-0 tabular-nums">
              {shownCount}
            </Badge>
          </SidebarMenuBadge>
          {/* The rail hides the badge, so a dot on the icon says that something is new. */}
          <span
            aria-hidden
            className="bg-destructive ring-sidebar pointer-events-none absolute top-1 left-5 hidden size-2 rounded-full ring-2 group-data-[collapsible=icon]:block"
          />
        </>
      )}
    </SidebarMenuItem>
  );
}
