"use client";

import type { ReactNode } from "react";
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
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import { useChatChannelListRealtime } from "@/features/chat/hooks/use-chat-realtime";
import { ChatChannelPurpose } from "@/features/chat/schemas";
import { isCyberscoreClosed } from "@/features/cyberscore/lib/cyberscore-access";
import { BoardsNavItem } from "./sidebar-boards-nav";
import { ChatChannelNavItem, ChatNavItem } from "./sidebar-chat-nav";
import { SidebarItem } from "./sidebar-item";

/** One row of Kazan's navigation, rendered inside a `SidebarMenu`. */
export interface KazanNavRow {
  id: string;
  node: ReactNode;
}

export interface KazanNavGroups {
  /** The security operations pages. */
  operations: KazanNavRow[];
  /** The tickets and the team's conversations. */
  work: KazanNavRow[];
}

/**
 * The rows of Kazan's navigation, shared by both layouts: the modern menu
 * shows them in two labelled sections, the classic sidebar in one list. The
 * Cyberscore row is left out for a guest of the organization, whom the API
 * refuses the cyberscore; each channel row shows only once its channel
 * exists. The chat rows follow channel changes on every page, not only in
 * the chat view.
 */
export function useKazanNavGroups(): KazanNavGroups {
  const t = useTranslations("sidebar");
  const { data: meResponse } = useGetMe();
  const me = meResponse?.data;
  const currentUserId = me?.id;
  const cyberscoreClosed = isCyberscoreClosed(me, me?.defaultOrganizationId);
  useChatChannelListRealtime();

  const operations: KazanNavRow[] = [
    { id: "home", node: <SidebarItem href="/home" label={t("home")} icon={House} /> },
    {
      id: "incidents",
      node: <SidebarItem href="/incidents" label={t("incidents")} icon={Siren} />,
    },
    {
      id: "vulnerabilities",
      node: <SidebarItem href="/vulnerabilities" label={t("vulnerabilities")} icon={ShieldAlert} />,
    },
    {
      id: "disasterDrills",
      node: <SidebarItem href="/disaster-drills" label={t("disasterDrills")} icon={LifeBuoy} />,
    },
    {
      id: "backupRuns",
      node: <SidebarItem href="/backup-runs" label={t("backupRuns")} icon={DatabaseBackup} />,
    },
    {
      id: "chaosEngineering",
      node: <SidebarItem href="/chaos-engineering" label={t("chaosEngineering")} icon={Tornado} />,
    },
  ];
  if (!cyberscoreClosed) {
    operations.push({
      id: "cyberscore",
      node: <SidebarItem href="/cyberscore" label={t("cyberscore")} icon={Gauge} />,
    });
  }

  const work: KazanNavRow[] = [
    {
      id: "tickets",
      node: (
        <SidebarItem
          href={`/tickets?assigneeIds=${currentUserId}`}
          label={t("tickets")}
          icon={Ticket}
        />
      ),
    },
    {
      id: "dailyUpdates",
      node: (
        <ChatChannelNavItem
          purpose={ChatChannelPurpose.DAILY_UPDATES}
          label={t("dailyUpdates")}
          icon={Newspaper}
        />
      ),
    },
    {
      id: "pullRequests",
      node: (
        <ChatChannelNavItem
          purpose={ChatChannelPurpose.PULL_REQUESTS}
          label={t("pullRequests")}
          icon={GitPullRequest}
        />
      ),
    },
    { id: "chats", node: <ChatNavItem /> },
    { id: "boards", node: <BoardsNavItem /> },
  ];

  return { operations, work };
}
