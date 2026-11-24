"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { AlertCircle, Archive, Loader2, Lock, MessagesSquare, type LucideIcon } from "lucide-react";
import { cn } from "@oba/ui/lib/utils";
import { badgeVariants } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { UserAvatarStack, type UserAvatarUser } from "@/components/ui/user-avatar";
import { useChatOpenChannelId } from "../hooks/use-chat-channels";
import { useChatThreads } from "../hooks/use-chat-messages";
import {
  chatConversationMentions,
  chatConversationUnread,
  chatThreadNotifications,
  useChatUnread,
} from "../hooks/use-mark-read";
import type { ChatThreadResponse, ChatThreadStatus } from "../schemas";
import { ChatThreadActivity } from "./chat-thread-summary";

/** The tabs of the list, one per effective status, in the order shown. */
const THREAD_TABS: readonly {
  status: ChatThreadStatus;
  label: "active" | "closed" | "archived";
  empty: "emptyActive" | "emptyClosed" | "emptyArchived";
  Icon: LucideIcon;
}[] = [
  { status: "ACTIVE", label: "active", empty: "emptyActive", Icon: MessagesSquare },
  { status: "CLOSED", label: "closed", empty: "emptyClosed", Icon: Lock },
  { status: "ARCHIVED", label: "archived", empty: "emptyArchived", Icon: Archive },
];

function isThreadStatus(value: string): value is ChatThreadStatus {
  return THREAD_TABS.some((tab) => tab.status === value);
}

/** A thread row shows this many member avatars before "+N". */
const LIST_AVATARS = 3;

/** Notification counts above this show as "99+". */
const COUNT_MAX = 99;

/** A notification count as shown in a badge: "99+" above 99. */
function shownCount(count: number): string {
  return count > COUNT_MAX ? `${COUNT_MAX}+` : String(count);
}

/** Reads out a notification count, saying "more than 99" above 99 as the badge does. */
function useNotificationsLabel(): (count: number) => string {
  const t = useTranslations("chat.thread.list");
  return (count) =>
    count > COUNT_MAX ? t("notificationsCapped", { count: COUNT_MAX }) : t("notifications", { count });
}

/** The red notification badge; red always means a notification, as in Discord. */
const NOTIFICATION_BADGE_CLASS =
  "h-4 min-w-4 shrink-0 justify-center rounded-none px-1 py-0 text-[10px] leading-none tabular-nums";

/** The neutral mark of unread messages without a notification. */
const UNREAD_DOT_CLASS = "bg-foreground size-2 shrink-0";

function memberAvatars(thread: ChatThreadResponse): UserAvatarUser[] {
  return thread.members.map((member) => ({
    firstName: member.firstName ?? undefined,
    lastName: member.lastName ?? undefined,
    email: member.email ?? undefined,
  }));
}

interface ChatThreadListProps {
  /** The thread open in the panel, marked in the list. */
  activeThreadId: string | null;
  onOpen: (threadId: string) => void;
}

/**
 * The "Threads" control of the channel header: a popover with the active,
 * the closed and the archived threads, most recent activity first, paged by
 * keyset.
 * A list is fetched only while its tab is shown.
 */
export function ChatThreadList({ activeThreadId, onOpen }: ChatThreadListProps) {
  const t = useTranslations("chat.thread.list");
  const [open, setOpen] = useState(false);
  const [tab, setTab] = useState<ChatThreadStatus>("ACTIVE");
  const { data: unread } = useChatUnread();
  const notificationsLabel = useNotificationsLabel();
  const channelId = useChatOpenChannelId();
  const { mentionCount, hasUnread } = chatThreadNotifications(unread, channelId);
  const stateLabel = mentionCount > 0 ? notificationsLabel(mentionCount) : hasUnread ? t("hasUnread") : null;

  const openThread = (threadId: string) => {
    setOpen(false);
    onOpen(threadId);
  };

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button variant="outline" size="sm" className="border-border h-8 gap-1.5 text-xs">
          <MessagesSquare className="h-3.5 w-3.5" />
          {t("button")}
          {stateLabel && <span className="sr-only">, {stateLabel}</span>}
          {mentionCount > 0 ? (
            <span aria-hidden className={cn(badgeVariants({ variant: "destructive" }), NOTIFICATION_BADGE_CLASS)}>
              {shownCount(mentionCount)}
            </span>
          ) : (
            hasUnread && <span aria-hidden className={UNREAD_DOT_CLASS} />
          )}
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" className="flex w-[min(24rem,calc(100vw-2rem))] flex-col gap-2 p-3">
        <h2 className="text-foreground text-sm font-semibold">{t("title")}</h2>
        <Tabs value={tab} onValueChange={(value) => isThreadStatus(value) && setTab(value)}>
          <TabsList className="w-full">
            {THREAD_TABS.map(({ status, label }) => (
              <TabsTrigger key={status} value={status} className="flex-1">
                {t(label)}
              </TabsTrigger>
            ))}
          </TabsList>
          {THREAD_TABS.map(({ status, empty, Icon }) => (
            <TabsContent key={status} value={status}>
              <ThreadPage
                status={status}
                emptyLabel={t(empty)}
                EmptyIcon={Icon}
                enabled={open && tab === status}
                activeThreadId={activeThreadId}
                onOpen={openThread}
              />
            </TabsContent>
          ))}
        </Tabs>
      </PopoverContent>
    </Popover>
  );
}

interface ThreadPageProps {
  status: ChatThreadStatus;
  emptyLabel: string;
  EmptyIcon: LucideIcon;
  enabled: boolean;
  activeThreadId: string | null;
  onOpen: (threadId: string) => void;
}

function ThreadPage({ status, emptyLabel, EmptyIcon, enabled, activeThreadId, onOpen }: ThreadPageProps) {
  const t = useTranslations("chat.thread.list");
  const notificationsLabel = useNotificationsLabel();
  const { data: unread } = useChatUnread();
  const channelId = useChatOpenChannelId();
  const {
    data,
    error,
    isPending,
    isError,
    isFetching,
    refetch,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    isFetchNextPageError,
  } = useChatThreads(channelId, status, { enabled });
  // Only active threads count as unread; closed and archived ones never show a mark.
  const counted = status === "ACTIVE";
  const threads = data?.pages.flatMap((page) => page.items) ?? [];

  if (isPending) {
    return (
      <div className="text-muted-foreground flex items-center justify-center gap-2 py-8 text-xs">
        <Loader2 className="h-4 w-4 animate-spin" />
        {t("loading")}
      </div>
    );
  }

  if (isError && !data) {
    return (
      <div role="alert" className="flex flex-col items-center gap-2 py-6 text-center">
        <AlertCircle className="text-destructive h-5 w-5" />
        <p className="text-foreground text-xs font-medium">{t("loadError")}</p>
        {error?.message && <p className="text-muted-foreground text-xs">{error.message}</p>}
        <Button variant="outline" size="sm" className="h-7" disabled={isFetching} onClick={() => void refetch()}>
          {t("retry")}
        </Button>
      </div>
    );
  }

  if (threads.length === 0) {
    return (
      <div className="text-muted-foreground flex flex-col items-center gap-2 py-8 text-center text-xs">
        <EmptyIcon className="h-5 w-5" />
        {emptyLabel}
      </div>
    );
  }

  return (
    <div className="flex max-h-[60vh] flex-col gap-1 overflow-y-auto">
      <ul className="flex flex-col gap-0.5">
        {threads.map((thread) => {
          const unreadCount = counted ? chatConversationUnread(unread, thread.channelId, thread.id) : 0;
          const mentionCount = counted ? chatConversationMentions(unread, thread.channelId, thread.id) : 0;
          return (
            <li key={thread.id}>
              <button
                type="button"
                onClick={() => onOpen(thread.id)}
                aria-current={thread.id === activeThreadId ? "true" : undefined}
                className={cn(
                  "hover:bg-muted flex w-full items-center gap-2.5 rounded-md px-2 py-1.5 text-left transition-colors",
                  "focus-visible:ring-ring focus-visible:ring-2 focus-visible:outline-none",
                  thread.id === activeThreadId && "bg-muted",
                )}
              >
                <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                  <span className="flex min-w-0 items-center gap-1.5">
                    <span
                      className={cn(
                        "truncate text-sm",
                        unreadCount > 0 ? "text-foreground font-semibold" : "text-foreground/80 font-normal",
                      )}
                    >
                      {thread.title}
                    </span>
                    {mentionCount > 0 ? (
                      <span
                        aria-hidden
                        className={cn(badgeVariants({ variant: "destructive" }), NOTIFICATION_BADGE_CLASS)}
                      >
                        @{shownCount(mentionCount)}
                      </span>
                    ) : (
                      unreadCount > 0 && <span aria-hidden className={UNREAD_DOT_CLASS} />
                    )}
                    {unreadCount > 0 && (
                      <span className="sr-only">
                        , {t("unread", { count: unreadCount })}
                        {mentionCount > 0 && `, ${notificationsLabel(mentionCount)}`}
                      </span>
                    )}
                  </span>
                  <ChatThreadActivity messageCount={thread.messageCount} lastMessageAt={thread.lastMessageAt} />
                </div>
                <UserAvatarStack
                  users={memberAvatars(thread)}
                  total={thread.rootMessage.thread?.memberCount}
                  max={LIST_AVATARS}
                  size="xs"
                />
              </button>
            </li>
          );
        })}
      </ul>
      {isFetchNextPageError ? (
        <div role="alert" className="flex items-center justify-center gap-2 py-1 text-xs">
          <AlertCircle className="text-destructive h-4 w-4 shrink-0" />
          <span className="text-foreground">{t("loadMoreError")}</span>
          <Button variant="outline" size="sm" className="h-7" onClick={() => void fetchNextPage()}>
            {t("retry")}
          </Button>
        </div>
      ) : (
        hasNextPage && (
          <Button
            variant="ghost"
            size="sm"
            className="h-7 self-center text-xs"
            disabled={isFetchingNextPage}
            onClick={() => void fetchNextPage()}
          >
            {isFetchingNextPage && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
            {t("loadMore")}
          </Button>
        )
      )}
    </div>
  );
}
