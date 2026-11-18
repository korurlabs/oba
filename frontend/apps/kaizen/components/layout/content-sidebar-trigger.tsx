"use client";

import { useTranslations } from "next-intl";
import { usePathname } from "@oba/shared/libs/i18n/navigation";
import { Separator } from "@oba/ui/components/separator";
import { SidebarTrigger } from "@oba/ui/components/sidebar";

/**
 * The sidebar collapse/expand control, rendered in the CONTENT area rather
 * than inside the sidebar -- the Kagi portal arrangement (panel icon, vertical
 * separator, then the breadcrumb row).
 *
 * It lives out here because it is the only way back out of a collapsed
 * sidebar, so it must stay on screen in every state: expanded, icon-collapsed
 * and mobile (where the sidebar is an off-canvas Sheet and nothing else
 * reveals it).
 */
export function ContentSidebarTrigger({ className }: { className?: string }) {
  const t = useTranslations("sidebar");

  return (
    <div className={className}>
      <SidebarTrigger
        className="shrink-0"
        aria-label={t("toggleSidebar")}
        title={t("toggleSidebar")}
      />
      <Separator orientation="vertical" className="data-[orientation=vertical]:h-4" />
    </div>
  );
}

/**
 * Standalone row carrying `ContentSidebarTrigger` for routes that have no
 * breadcrumb bar of their own.
 *
 * Board routes render their trigger INSIDE the breadcrumb/tabs row
 * (`board-tabs.tsx`) so the panel icon sits to the left of the breadcrumb, as
 * asked. Hence the bar stands down on every `/board/...` path. That is a rule
 * about the usual case, not an exact statement: a `/board/...` path that
 * renders no `BoardTabs` (an id-based URL, which only redirects; an unknown
 * board; a path deeper than any board page) has to render the trigger
 * itself, as the dashboard's not-found screen and `UnknownBoardGate` in the
 * board route do. Keeping the two in sync is the reason this check lives
 * next to the component it suppresses.
 */
export function ContentSidebarTriggerBar() {
  const pathname = usePathname();
  const boardRouteOwnsTrigger = pathname.startsWith("/board/");

  if (boardRouteOwnsTrigger) return null;

  return (
    <ContentSidebarTrigger className="border-border bg-background flex shrink-0 items-center gap-2 border-b px-4 py-2" />
  );
}
