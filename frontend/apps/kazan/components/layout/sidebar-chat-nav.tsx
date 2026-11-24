"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import { ChevronRight, MessagesSquare, type LucideIcon } from "lucide-react";
import { Link, usePathname } from "@oba/shared/libs/i18n/navigation";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@oba/ui/components/collapsible";
import {
  SidebarMenuAction,
  SidebarMenuBadge,
  SidebarMenuButton,
  SidebarMenuItem,
} from "@oba/ui/sky/sidebar";
import { cn } from "@oba/ui/lib/utils";
import { Badge } from "@/components/ui/badge";
import { ChatChannelList } from "@/features/chat/components/chat-channel-list";
import { useChatChannelOrder } from "@/features/chat/hooks/use-chat-channel-order";
import { useChatChannels } from "@/features/chat/hooks/use-chat-channels";
import { chatChannelPath, CHATS_PATH } from "@/features/chat/lib/chat-route";
import type { ChatChannelPurpose } from "@/features/chat/schemas";
import { useSidebarSectionOpen } from "@/hooks/use-sidebar-section-open";
import { useSidebarContext } from "./sidebar-context";
import { useChatUnreadCount } from "./sidebar-chat-unread";
import { BADGE_MAX, SIDEBAR_ROW_CLASS, SIDEBAR_ROW_TRAILING_CLASS } from "./sidebar-item";
import { isKazanShut, useKazanAccess } from "./use-kazan-access";

const CHAT_CHANNELS_OPEN_STORAGE_KEY = "kazan-sidebar-chat-channels-open";

const NO_CHANNEL_IDS: ReadonlySet<string> = new Set<string>();

interface UnreadBadge {
  /** The count as shown: "7", or "99+" above 99. */
  shownCount: string;
  /** What the shown count means, for screen readers and the rail's tooltip; undefined when nothing is unread. */
  countLabel: string | undefined;
}

/** The shared unread count of some channels, with its label for screen readers and the rail's tooltip. */
function useChannelsUnreadBadge(channelIds: ReadonlySet<string>): UnreadBadge {
  const t = useTranslations("sidebar");
  const { count, mentions, shownCount } = useChatUnreadCount(channelIds);

  let countLabel: string | undefined;
  if (count === 0) countLabel = undefined;
  else if (mentions > BADGE_MAX) countLabel = t("updatesUnreadMentionsCapped", { count: shownCount, mentions: BADGE_MAX });
  else if (mentions > 0) countLabel = t("updatesUnreadMentions", { count: shownCount, mentions });
  else countLabel = t("updatesUnread", { count: shownCount });
  return { shownCount, countLabel };
}

/** The red count of a row, and the dot on its icon that stands in for it in the collapsed rail. */
function RowUnreadBadge({ shownCount, className }: { shownCount: string; className?: string }) {
  return (
    <>
      <SidebarMenuBadge aria-hidden className={cn(SIDEBAR_ROW_TRAILING_CLASS, className)}>
        <Badge variant="destructive" className="h-5 min-w-5 justify-center rounded-full px-1.5 py-0 tabular-nums">
          {shownCount}
        </Badge>
      </SidebarMenuBadge>
      <span
        aria-hidden
        className="bg-destructive ring-sidebar pointer-events-none absolute top-1 left-5 hidden size-2 rounded-full ring-2 group-data-[collapsible=icon]:block"
      />
    </>
  );
}

interface ChatChannelNavItemProps {
  /** A purpose that one live channel of the organization holds at most. */
  purpose: ChatChannelPurpose;
  label: string;
  icon: LucideIcon;
}

/**
 * A root row that opens the one channel holding `purpose` (the daily updates,
 * the pull requests channel), so the channels the team reads every day are
 * one click away. The label is fixed rather than the channel's name, so an
 * admin's rename does not move the row's meaning. The row is left out while
 * no live channel holds the purpose, and while the channel list loads or
 * failed to load; the Chats list below shows that error with Retry.
 *
 * The row is active on its channel's path and on the screens under it: the
 * daily update screens under `/daily-updates`, the pull request share screens
 * under `/pull-requests`.
 */
export function ChatChannelNavItem({ purpose, label, icon: Icon }: ChatChannelNavItemProps) {
  const pathname = usePathname();
  const { closeMobile } = useSidebarContext();
  const { data: channels } = useChatChannels();
  const holder = channels?.find((channel) => channel.purpose === purpose);
  const holderIds = useMemo<ReadonlySet<string>>(
    () => (holder ? new Set([holder.id]) : NO_CHANNEL_IDS),
    [holder]
  );
  const { shownCount, countLabel } = useChannelsUnreadBadge(holderIds);

  if (!holder) return null;

  const path = chatChannelPath(holder);
  const isActive = pathname === path || pathname.startsWith(`${path}/`);

  return (
    <SidebarMenuItem>
      <SidebarMenuButton
        asChild
        isActive={isActive}
        tooltip={countLabel ? `${label} (${countLabel})` : label}
        className={cn(SIDEBAR_ROW_CLASS, countLabel && "pr-9")}
      >
        <Link href={path} onClick={closeMobile}>
          <Icon />
          <span>
            {label}
            {countLabel && <span className="sr-only">, {countLabel}</span>}
          </span>
        </Link>
      </SidebarMenuButton>
      {countLabel && <RowUnreadBadge shownCount={shownCount} />}
    </SidebarMenuItem>
  );
}

/**
 * The "Chats" row with the GENERAL channels under it; the channels of the
 * other purposes have their own root rows. The name opens the chat view; the
 * chevron opens and closes the channel list without navigating. The list
 * starts open, and the reader's choice is remembered in this browser.
 *
 * Built from the menu primitives rather than `SidebarItem`, because the chevron
 * and the list have to sit inside the row's own menu item. The row's badge
 * counts the listed channels only, so a closed list still shows that something
 * is new there; the collapsed rail hides the chevron and the list and shows
 * the dot, as for any other row.
 */
export function ChatNavItem() {
  const t = useTranslations("sidebar");
  const pathname = usePathname();
  const { closeMobile } = useSidebarContext();
  const [open, setOpen] = useSidebarSectionOpen(CHAT_CHANNELS_OPEN_STORAGE_KEY);
  const { listedChannels } = useChatChannelOrder();
  // No organization, or Kazan closed in it: the channel list is never loaded, so it is not shown at all.
  const kazanClosed = isKazanShut(useKazanAccess());

  const listedIds = useMemo<ReadonlySet<string>>(
    () => new Set(listedChannels.map((channel) => channel.id)),
    [listedChannels]
  );
  const { shownCount, countLabel } = useChannelsUnreadBadge(listedIds);

  // `/chats` and the GENERAL channels under it; the channels with a root row have their own paths.
  const isActive = pathname === CHATS_PATH || pathname.startsWith(`${CHATS_PATH}/`);
  const label = t("chats");

  return (
    <Collapsible asChild open={open} onOpenChange={setOpen}>
      <SidebarMenuItem>
        <SidebarMenuButton
          asChild
          isActive={isActive}
          tooltip={countLabel ? `${label} (${countLabel})` : label}
          className={cn(SIDEBAR_ROW_CLASS, countLabel && "group-has-data-[sidebar=menu-action]/menu-item:pr-14")}
        >
          <Link href={CHATS_PATH} onClick={closeMobile}>
            <MessagesSquare />
            <span>
              {label}
              {countLabel && <span className="sr-only">, {countLabel}</span>}
            </span>
          </Link>
        </SidebarMenuButton>
        {countLabel && <RowUnreadBadge shownCount={shownCount} className="right-7" />}
        <CollapsibleTrigger asChild>
          <SidebarMenuAction
            aria-label={t(open ? "hideChannels" : "showChannels")}
            className={SIDEBAR_ROW_TRAILING_CLASS}
          >
            <ChevronRight className={cn("transition-transform", open && "rotate-90")} />
          </SidebarMenuAction>
        </CollapsibleTrigger>
        {/* The GENERAL channels, in the reader's order; hidden in the collapsed rail. */}
        <CollapsibleContent>{!kazanClosed && <ChatChannelList />}</CollapsibleContent>
      </SidebarMenuItem>
    </Collapsible>
  );
}
