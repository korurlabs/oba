"use client";

import { useTranslations } from "next-intl";
import {
  DatabaseBackup,
  Gauge,
  GitPullRequest,
  House,
  LifeBuoy,
  Newspaper,
  ShieldAlert,
  Siren,
  Ticket,
  Tornado,
} from "lucide-react";
import {
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarMenu,
} from "@oba/ui/sky/sidebar";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import { useChatChannelListRealtime } from "@/features/chat/hooks/use-chat-realtime";
import { ChatChannelPurpose } from "@/features/chat/schemas";
import { BoardsNavItem } from "./sidebar-boards-nav";
import { ChatChannelNavItem, ChatNavItem } from "./sidebar-chat-nav";
import { SidebarItem } from "./sidebar-item";

/**
 * Kazan's navigation inside the sky shell's floating panel, in two labelled
 * sections like the other sky apps: Operations holds the security operations
 * pages, Work the tickets and the team's conversations. The daily updates and
 * pull requests channels sit in Work, next to the Chats and Boards rows; each
 * channel row shows only once its channel exists. Settings sits apart at the
 * panel's foot, drawn by the shell. When the rows do not fit, the panel
 * scrolls.
 *
 * The rows close the menu sheet of a narrow window on a click, and the Chats
 * and Boards rows hide their channel list, board tree and "+" while Kazan is
 * shut in the selected organization.
 */
export function KazanMenu() {
  const t = useTranslations("sidebar");
  const { data: meResponse } = useGetMe();
  const currentUserId = meResponse?.data?.id;
  // The chat rows follow channel changes on every page, not only in the chat view.
  useChatChannelListRealtime();

  return (
    <>
      <SidebarGroup className="py-0">
        <SidebarGroupLabel>{t("operations")}</SidebarGroupLabel>
        <SidebarGroupContent>
          <SidebarMenu className="gap-0.5">
            <SidebarItem href="/home" label={t("home")} icon={House} />
            <SidebarItem href="/incidents" label={t("incidents")} icon={Siren} />
            <SidebarItem href="/vulnerabilities" label={t("vulnerabilities")} icon={ShieldAlert} />
            <SidebarItem href="/disaster-drills" label={t("disasterDrills")} icon={LifeBuoy} />
            <SidebarItem href="/backup-runs" label={t("backupRuns")} icon={DatabaseBackup} />
            <SidebarItem href="/chaos-engineering" label={t("chaosEngineering")} icon={Tornado} />
            <SidebarItem href="/cyberscore" label={t("cyberscore")} icon={Gauge} />
          </SidebarMenu>
        </SidebarGroupContent>
      </SidebarGroup>
      <SidebarGroup className="py-0">
        <SidebarGroupLabel>{t("work")}</SidebarGroupLabel>
        <SidebarGroupContent>
          <SidebarMenu className="gap-0.5">
            <SidebarItem
              href={`/tickets?assigneeIds=${currentUserId}`}
              label={t("tickets")}
              icon={Ticket}
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
          </SidebarMenu>
        </SidebarGroupContent>
      </SidebarGroup>
    </>
  );
}
