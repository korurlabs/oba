"use client";

import { useTranslations } from "next-intl";
import { GitPullRequest, House, Newspaper, Settings, Siren, Ticket } from "lucide-react";
import {
  Sidebar as ShadcnSidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
} from "@oba/ui/components/sidebar";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import { useChatChannelListRealtime } from "@/features/chat/hooks/use-chat-realtime";
import { ChatChannelPurpose } from "@/features/chat/schemas";
import { BoardsNavItem } from "./sidebar-boards-nav";
import { ChatChannelNavItem, ChatNavItem } from "./sidebar-chat-nav";
import { SidebarFooterMenu } from "./sidebar-footer-menu";
import { SidebarItem } from "./sidebar-item";
import { SidebarOrgSwitcher } from "./sidebar-org-switcher";
import { SidebarResizeHandle } from "./sidebar-resize-handle";

/**
 * The Kazan app shell.
 *
 * Kazan has no global top header: the sidebar runs the full height of the
 * viewport and owns the chrome that used to sit in the header -- organization
 * switcher at the top, navigation in the middle, account menu at the bottom.
 * Kazan is a standalone app, so there is no app switcher; the Settings row
 * leads to the Kazan settings screens, whose navigation row links to the
 * account app (profile, invitations, general settings) and to Admin
 * (organizations). The collapse toggle and the language switcher
 * live elsewhere now: the toggle at the left edge of the content area
 * (`content-sidebar-trigger.tsx`), the language list inside the account menu.
 *
 * The navigation is one list: the daily updates and pull requests channels
 * sit at the root, next to the Chats and Boards sections, so the places the
 * team reads every day are one click away. When the rows do not fit, the
 * whole sidebar content scrolls.
 *
 * `variant="inset"` matches the Kagi portal: the page ground is the tinted
 * `--sidebar` surface and the content is a rounded, bordered `--background`
 * panel floated on top of it (see `SidebarInset` in the dashboard layout).
 */
export function Sidebar() {
  const t = useTranslations("sidebar");
  const { data: meResponse } = useGetMe();
  const currentUserId = meResponse?.data?.id;
  // The chat rows below follow channel changes on every page, not only in the chat view.
  useChatChannelListRealtime();

  return (
    <ShadcnSidebar variant="inset" collapsible="icon">
      {/* Organization switcher */}
      <SidebarHeader>
        <SidebarOrgSwitcher />
      </SidebarHeader>

      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupLabel>{t("navigation")}</SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu>
              <SidebarItem href="/home" label={t("home")} icon={House} />
              <SidebarItem
                href={`/tickets?assigneeIds=${currentUserId}`}
                label={t("tickets")}
                icon={Ticket}
              />
              <SidebarItem
                href="/incidents"
                label={t("incidents")}
                icon={Siren}
              />
              <ChatChannelNavItem
                purpose={ChatChannelPurpose.DAILY_UPDATES}
                label={t("dailyUpdates")}
                icon={Newspaper}
              />
              <ChatChannelNavItem
                purpose={ChatChannelPurpose.PULL_REQUESTS}
                label={t("pullRequests")}
                icon={GitPullRequest}
              />
              <ChatNavItem />
              <BoardsNavItem />
              <SidebarItem
                href="/settings/columns"
                label={t("settings")}
                icon={Settings}
                activePrefixes={["/settings"]}
              />
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>

      {/* Account menu (language switcher lives inside it) */}
      <SidebarFooter>
        <SidebarFooterMenu />
      </SidebarFooter>

      {/* Drag-to-resize rail */}
      <SidebarResizeHandle />
    </ShadcnSidebar>
  );
}
