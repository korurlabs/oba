"use client";

import { useTranslations } from "next-intl";
import { Separator } from "@oba/ui/sky/separator";
import { SidebarTrigger } from "@oba/ui/sky/sidebar";

/**
 * The sidebar collapse and expand control of the classic layout, rendered in
 * the CONTENT area rather than inside the sidebar (panel icon, vertical
 * separator, then the breadcrumb row).
 *
 * It lives out here because it is the only way back out of a collapsed
 * sidebar, so it must stay on screen in every state: expanded, icon-collapsed
 * and mobile (where the sidebar is an off-canvas sheet and nothing else
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
 * Standalone row carrying `ContentSidebarTrigger` for pages that have no
 * board bar of their own.
 *
 * A board page renders its trigger inside the board bar (`board-tabs.tsx`),
 * so this row hides while the content area holds an element marked
 * `data-board-bar` (the bar or its loading placeholder). The check is CSS
 * rather than a pathname match because a `/board/...` path does not always
 * render the bar: an unknown board shows the not-found screen, and a board
 * of an organization without Kazan shows the no-access panel. On a phone the
 * sidebar is an off-canvas sheet that only this trigger opens, so those
 * screens must keep it. Being CSS, the server-rendered page is already right
 * and nothing shifts on hydration. `ClassicAppShell` names the group.
 */
export function ContentSidebarTriggerBar() {
  return (
    <ContentSidebarTrigger className="border-border bg-background flex shrink-0 items-center gap-2 border-b px-4 py-2 group-has-[[data-board-bar]]/classic-content:hidden" />
  );
}
