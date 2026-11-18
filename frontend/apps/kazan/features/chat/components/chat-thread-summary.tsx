"use client";

import { memo } from "react";
import { useFormatter, useNow, useTranslations } from "next-intl";
import { Archive, ChevronRight, Lock, MessagesSquare } from "lucide-react";
import { cn } from "@oba/ui/lib/utils";
import { UserAvatarStack, type UserAvatarUser } from "@/components/ui/user-avatar";
import type { ChatThreadStatus, ChatThreadSummaryResponse, ChatUserResponse } from "../schemas";

/** Relative times ("5 minutes ago") are refreshed this often. */
const RELATIVE_TIME_REFRESH_MS = 60_000;

/** The summary shows this many member avatars before "+N". */
const SUMMARY_AVATARS = 3;

/** Turns the users of a page into avatars, in the order of `userIds`, skipping unknown IDs. */
export function chatThreadAvatarUsers(
  userIds: readonly string[],
  users: Record<string, ChatUserResponse>,
): UserAvatarUser[] {
  return userIds.flatMap((userId) => {
    const user = users[userId];
    if (!user) return [];
    return [
      {
        firstName: user.firstName ?? undefined,
        lastName: user.lastName ?? undefined,
        email: user.email ?? undefined,
      },
    ];
  });
}

/**
 * The time to show for a server timestamp in a relative time ("5 minutes
 * ago"): the server's clock may run a little ahead of the browser's, and an
 * event that already happened never shows as in the future.
 */
export function chatPastTime(at: string, now: Date): Date {
  return new Date(Math.min(new Date(at).getTime(), now.getTime()));
}

/**
 * The reply count and the time of the last reply of a thread, as one line:
 * "3 replies · 5 minutes ago". A thread without replies shows only that.
 */
export function ChatThreadActivity({
  messageCount,
  lastMessageAt,
  className,
}: {
  messageCount: number;
  lastMessageAt: string;
  className?: string;
}) {
  const t = useTranslations("chat.thread.summary");
  const format = useFormatter();
  const now = useNow({ updateInterval: RELATIVE_TIME_REFRESH_MS });

  return (
    <span className={cn("text-muted-foreground inline-flex min-w-0 items-center gap-1.5 text-xs", className)}>
      <span className="shrink-0">{t("replies", { count: messageCount })}</span>
      {messageCount > 0 && (
        <>
          <span aria-hidden>·</span>
          <time dateTime={lastMessageAt} className="truncate" suppressHydrationWarning>
            {format.relativeTime(chatPastTime(lastMessageAt, now), now)}
          </time>
        </>
      )}
    </span>
  );
}

/**
 * The chip of a closed or archived thread; an active thread shows none, as
 * active is what a reader expects.
 */
export function ChatThreadStatusChip({ status, className }: { status: ChatThreadStatus; className?: string }) {
  const t = useTranslations("chat.thread.status");
  if (status === "ACTIVE") return null;
  const Icon = status === "CLOSED" ? Lock : Archive;
  return (
    <span
      className={cn(
        "border-border bg-muted text-muted-foreground inline-flex shrink-0 items-center gap-1 rounded-full border px-1.5 py-px text-[11px] font-medium",
        className,
      )}
    >
      <Icon className="h-3 w-3" aria-hidden />
      {status === "CLOSED" ? t("closed") : t("archived")}
    </span>
  );
}

interface ChatThreadSummaryProps {
  thread: ChatThreadSummaryResponse;
  /** The users of the root message's page; the preview members are among them. */
  users: Record<string, ChatUserResponse>;
  /** True while this thread is open in the panel. */
  active?: boolean;
  onOpen: (threadId: string) => void;
}

/**
 * The line under a message that started a thread, Discord style: the title,
 * the reply count, the time of the last reply, the chip of a closed or
 * archived thread and up to three member avatars with "+N". Clicking it
 * opens the thread in the side panel.
 */
export const ChatThreadSummary = memo(function ChatThreadSummary({
  thread,
  users,
  active = false,
  onOpen,
}: ChatThreadSummaryProps) {
  const t = useTranslations("chat.thread");
  const avatars = chatThreadAvatarUsers(thread.memberPreviewIds, users);

  return (
    <button
      type="button"
      onClick={() => onOpen(thread.threadId)}
      aria-label={t("summary.open", { title: thread.title })}
      aria-current={active ? "true" : undefined}
      className={cn(
        "border-border bg-muted/30 hover:bg-muted group/thread flex w-full max-w-xl items-center gap-2.5 rounded-md border px-2.5 py-1.5 text-left transition-colors",
        "focus-visible:ring-ring focus-visible:ring-2 focus-visible:outline-none",
        active && "border-primary/50 bg-primary/5",
      )}
    >
      <MessagesSquare className="text-muted-foreground h-4 w-4 shrink-0" aria-hidden />
      <div className="flex min-w-0 flex-1 flex-col gap-0.5 sm:flex-row sm:items-center sm:gap-2.5">
        <span className="text-foreground truncate text-sm font-semibold">{thread.title}</span>
        <ChatThreadActivity
          messageCount={thread.messageCount}
          lastMessageAt={thread.lastMessageAt}
          className="text-primary shrink-0"
        />
      </div>
      <ChatThreadStatusChip status={thread.status} />
      <UserAvatarStack users={avatars} total={thread.memberCount} max={SUMMARY_AVATARS} size="xs" />
      <ChevronRight
        className="text-muted-foreground h-4 w-4 shrink-0 opacity-0 transition-opacity group-hover/thread:opacity-100"
        aria-hidden
      />
    </button>
  );
});
