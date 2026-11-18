"use client";

import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode, type Ref } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useSearchParams } from "next/navigation";
import { useSession } from "next-auth/react";
import { useLocale, useTranslations } from "next-intl";
import {
  AlertCircle,
  Building2,
  CalendarDays,
  GitPullRequest,
  Hash,
  Keyboard,
  Loader2,
  Lock,
  LogOut,
  Pencil,
  Plus,
  Settings2,
  Trash2,
  Users,
  X,
} from "lucide-react";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import { formatDateOnly } from "@oba/shared/lib/date-only";
import { Link, usePathname, useRouter } from "@oba/shared/libs/i18n/navigation";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { EmptyState } from "@/components/ui/empty-state";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { PullRequestChannelFilterPanel } from "@/features/pull-requests/components/pull-request-channel-filter-panel";
import { PULL_REQUEST_SHARE_CREATE_PATH } from "@/features/pull-requests/components/pull-request-share-paths";
import { usePullRequestChannelFilterParams } from "@/features/pull-requests/hooks/use-pull-request-channel-filter-params";
import { resolvePullRequestStatuses } from "@/features/pull-requests/lib/pull-request-channel-filter";
import { MissingUpdatesButton } from "@/features/updates/components/missing-updates-button";
import { UpdateFilterPanel } from "@/features/updates/components/update-filter-panel";
import { useUpdateFilterParams } from "@/features/updates/hooks/use-update-filter-params";
import { resolveUpdateDay, updateDayOf, updateToday } from "@/features/updates/lib/update-day";
import { firstReadableChatChannel, useChatChannelOrder } from "../hooks/use-chat-channel-order";
import { ChatReadableChannelProvider } from "../hooks/use-chat-channels";
import { useChatMessage, useChatOrganizationId } from "../hooks/use-chat-messages";
import { CHAT_KEYS, type ChatChannelFilter } from "../hooks/query-keys";
import { useChatParams } from "../hooks/use-chat-params";
import { useChatRealtime } from "../hooks/use-chat-realtime";
import { useChatShortcuts } from "../hooks/use-chat-shortcuts";
import { useChatUnreadMarker } from "../hooks/use-mark-read";
import { useTypingUsers } from "../hooks/use-typing-users";
import {
  CHAT_CHANNEL_CONTENT_PARAMS,
  chatChannelPath,
  chatRouteChannel,
  chatRouteHref,
  CHATS_PATH,
  DAILY_UPDATES_PATH,
  LEGACY_CHANNEL_ID_PARAM,
} from "../lib/chat-route";
import {
  ChatChannelPurpose,
  ChatChannelVisibility,
  type ChatChannelResponse,
  type ChatMessageResponse,
} from "../schemas";
import {
  ChatChannelDeleteDialog,
  ChatChannelDialog,
  ChatChannelLeaveDialog,
  useCanManageChatChannels,
} from "./chat-channel-dialog";
import { ChatChannelMembersDialog } from "./chat-channel-members-dialog";
import { ChatComposer, useChatCanWrite, type ChatComposerHandle } from "./chat-composer";
import { ChatConnectionBanner } from "./chat-connection-banner";
import { ChatMembersPopover } from "./chat-members-popover";
import { ChatMessageActionsProvider } from "./chat-message-actions";
import { ChatMessageList, type ChatMessageListHandle } from "./chat-message-list";
import { ChatPinsPopover } from "./chat-pins-popover";
import { ChatReactions } from "./chat-reactions";
import { ChatSearchPanel } from "./chat-search-panel";
import { ChatShortcutsDialog } from "./chat-shortcuts-dialog";
import { ChatThreadList } from "./chat-thread-list";
import { ChatThreadColumn, ChatThreadPanel } from "./chat-thread-panel";
import { ChatThreadSummary } from "./chat-thread-summary";

const NO_TYPING: readonly string[] = [];

/** Route of the screen that writes a daily update. */
export const UPDATE_CREATE_PATH = `${DAILY_UPDATES_PATH}/new`;

/**
 * The chat of the reader's organization: the open channel's messages (and,
 * in the daily updates channel, the daily update cards), read like a chat.
 * The live connection is held here once, so every list below it stays
 * current, whichever channel is open.
 */
export function ChatView() {
  const t = useTranslations("chat");
  const { status: sessionStatus } = useSession();
  const orgId = useChatOrganizationId();

  if (!orgId) {
    return (
      <div className="bg-background flex h-full flex-col">
        <ChatHeader orgId={undefined} />
        {sessionStatus !== "loading" && (
          <EmptyState
            Icon={Building2}
            title={t("list.noOrganization.title")}
            description={t("list.noOrganization.description")}
          />
        )}
      </div>
    );
  }

  // Keyed by organization: a switch starts over with that organization's
  // read position and scroll state.
  return <ChatChannel key={orgId} orgId={orgId} />;
}

function ChatHeader({
  orgId,
  channel,
  controls,
}: {
  orgId: string | undefined;
  /** The open channel; the header reads "Chat" until it is known. */
  channel?: ChatChannelResponse;
  controls?: ReactNode;
}) {
  const t = useTranslations("chat.channel");
  const tChannels = useTranslations("chat.channels");
  const tPullRequests = useTranslations("pullRequests");
  const canWrite = useChatCanWrite();
  const { data: meResponse } = useGetMe();
  const organizationName = meResponse?.data?.organizations?.find((organization) => organization.id === orgId)?.name;
  const Icon = channel?.visibility === ChatChannelVisibility.PRIVATE ? Lock : Hash;

  return (
    <header className="border-border flex flex-wrap items-center gap-x-3 gap-y-2 border-b px-4 py-3 lg:px-6">
      <div className="flex min-w-0 flex-1 items-center gap-2">
        <Icon className="text-muted-foreground h-5 w-5 shrink-0" aria-hidden />
        <h1 className="text-foreground truncate text-lg font-semibold">{channel?.name ?? t("title")}</h1>
        {channel?.visibility === ChatChannelVisibility.PRIVATE && (
          <span className="sr-only">{tChannels("private")}</span>
        )}
        {channel?.purpose === ChatChannelPurpose.DAILY_UPDATES && (
          <Badge variant="outline" className="shrink-0 px-1.5 py-0 text-[10px] font-medium">
            {tChannels("dailyUpdatesBadge")}
          </Badge>
        )}
        {organizationName && (
          <span className="text-muted-foreground hidden truncate text-sm sm:inline">{organizationName}</span>
        )}
      </div>
      {controls}
      {/* Daily updates land only in the daily updates channel, so only it offers to write one. */}
      {channel?.purpose === ChatChannelPurpose.DAILY_UPDATES && (
        <>
          <UpdateFilterPanel channelId={channel.id} />
          <MissingUpdatesButton />
          <Button asChild size="sm" className="h-8 gap-1.5">
            <Link href={UPDATE_CREATE_PATH}>
              <Plus className="h-4 w-4" />
              {t("postDailyUpdate")}
            </Link>
          </Button>
        </>
      )}
      {/* Every reader may narrow the pull requests channel; only a member who may write there shares. */}
      {channel?.purpose === ChatChannelPurpose.PULL_REQUESTS && (
        <>
          <PullRequestChannelFilterPanel channelId={channel.id} />
          {canWrite && (
            <Button asChild size="sm" className="h-8 gap-1.5">
              <Link href={PULL_REQUEST_SHARE_CREATE_PATH}>
                <GitPullRequest className="h-4 w-4" />
                {tPullRequests("shareButton")}
              </Link>
            </Button>
          )}
        </>
      )}
    </header>
  );
}

/** The header button that opens the keyboard shortcuts (also Mod+/). */
function ShortcutsButton({ onClick }: { onClick: () => void }) {
  const t = useTranslations("chat.shortcuts");
  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <Button variant="outline" size="icon-sm" className="border-border" aria-label={t("button")} onClick={onClick}>
            <Keyboard className="h-3.5 w-3.5" />
          </Button>
        </TooltipTrigger>
        <TooltipContent>
          <p>{t("button")}</p>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}

/**
 * Which channel the view shows: the one its path names (`/chats/<slug>`,
 * `/daily-updates`, `/pull-requests`). The URL is replaced, query kept, when
 * it is not that channel's own path: on `/chats` with an old `channelId`
 * link, after an admin renamed the open channel (its slug follows the name)
 * or gave it another purpose. When the path names no channel, names one the
 * reader does not see, or one they cannot read (an admin not on a private
 * channel), the URL is replaced with the first readable GENERAL channel in
 * the reader's order, or without one the first readable channel of any
 * purpose, and what pointed into the other channel (thread, jump target) is
 * dropped; with no readable channel at all it becomes `/chats`. A channel
 * that answers 404 `KAZ_NOT_015` was lost (the reader was removed, or it
 * became private or was deleted): it is skipped, the list is refetched, and
 * a notice names it.
 */
function useOpenChannel(orgId: string) {
  const queryClient = useQueryClient();
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const channelsQuery = useChatChannelOrder();
  const { channels, isSuccess, orderKnown } = channelsQuery;
  // The first channel is picked in the reader's saved order, so both must be in.
  const ready = isSuccess && orderKnown;
  const [lostIds, setLostIds] = useState<ReadonlySet<string>>(() => new Set());
  const [lostName, setLostName] = useState<string | null>(null);
  // Channels the reader deletes or leaves here: their 404 is expected and names no loss.
  const removingIdsRef = useRef(new Set<string>());

  const legacyChannelId = pathname === CHATS_PATH ? searchParams.get(LEGACY_CHANNEL_ID_PARAM) : null;
  const routeChannel =
    chatRouteChannel(pathname, channels) ??
    (legacyChannelId ? channels.find((channel) => channel.id === legacyChannelId) : undefined);
  // The channel the path named last: a rename changes its slug, and the view stays on it.
  const [followedId, setFollowedId] = useState(routeChannel?.id);
  if (routeChannel && routeChannel.id !== followedId) setFollowedId(routeChannel.id);
  const current = routeChannel ?? channels.find((channel) => channel.id === followedId);
  const readable = !!current && current.member && !lostIds.has(current.id);
  const fallback =
    ready && !readable
      ? (firstReadableChatChannel(channels, lostIds, ChatChannelPurpose.GENERAL) ??
        firstReadableChatChannel(channels, lostIds))
      : undefined;

  let target: string | undefined;
  if (readable) {
    target = chatRouteHref(chatChannelPath(current), searchParams, [LEGACY_CHANNEL_ID_PARAM]);
  } else if (ready) {
    target = chatRouteHref(fallback ? chatChannelPath(fallback) : CHATS_PATH, searchParams, [
      LEGACY_CHANNEL_ID_PARAM,
      ...CHAT_CHANNEL_CONTENT_PARAMS,
    ]);
  }
  const currentHref = chatRouteHref(pathname, searchParams);

  useEffect(() => {
    if (target === undefined || target === currentHref) return;
    router.replace(target);
  }, [target, currentHref, router]);

  const onChannelLost = useCallback(
    (channelId: string) => {
      setLostIds((ids) => new Set(ids).add(channelId));
      if (!removingIdsRef.current.has(channelId)) {
        setLostName(channels.find((channel) => channel.id === channelId)?.name ?? null);
      }
      void queryClient.invalidateQueries({ queryKey: CHAT_KEYS.channels(orgId) });
    },
    [channels, orgId, queryClient],
  );
  const dismissLost = useCallback(() => setLostName(null), []);
  const onRemoving = useCallback((channelId: string) => removingIdsRef.current.add(channelId), []);
  const onRemoveFailed = useCallback((channelId: string) => removingIdsRef.current.delete(channelId), []);

  return {
    channelsQuery,
    channel: readable ? current : undefined,
    /** The channels loaded and none of them can be read. */
    noChannel: ready && !readable && !fallback,
    lostName,
    onChannelLost,
    onRemoving,
    onRemoveFailed,
    dismissLost,
  };
}

function ChatChannel({ orgId }: { orgId: string }) {
  const t = useTranslations("chat.channel");
  const tChannels = useTranslations("chat.channels");
  const tComposer = useTranslations("chat.composer");
  const tList = useTranslations("chat.list");
  const [{ thread, jump, q, threadMessage }, setParams] = useChatParams();
  const { channelsQuery, channel, noChannel, lostName, onChannelLost, onRemoving, onRemoveFailed, dismissLost } =
    useOpenChannel(orgId);
  const channelId = channel?.id;
  const updatesChannel = channel?.purpose === ChatChannelPurpose.DAILY_UPDATES;
  const pullRequestsChannel = channel?.purpose === ChatChannelPurpose.PULL_REQUESTS;
  const {
    filter: updateFilter,
    listJump: updateListJump,
    setUpdateFilters,
  } = useUpdateChannelFilter(updatesChannel, jump);
  const {
    filter: pullRequestFilter,
    listJump: pullRequestListJump,
    setPullRequestFilters,
  } = usePullRequestChannelFilter(pullRequestsChannel, jump);
  // At most one is set, since each belongs to its own channel purpose; without a filter both pass `jump` on.
  const filter = updateFilter ?? pullRequestFilter;
  const listJump = updateFilter ? updateListJump : pullRequestListJump;
  const { onSignal, typingUserIds } = useTypingUsers();
  const { channelNewMessageCount, threadNewMessageCount, clearNewMessageCount } = useChatRealtime({
    threadId: thread,
    onSignal,
  });
  const canWrite = useChatCanWrite();
  const [replyTo, setReplyTo] = useState<ChatMessageResponse | null>(null);
  const [editing, setEditing] = useState<ChatMessageResponse | null>(null);
  // A thread being started on a message; it has no ID, so it is not in the URL.
  const [threadDraft, setThreadDraft] = useState<ChatMessageResponse | null>(null);
  // The thread was just created here: its composer takes the caret.
  const [focusThreadComposer, setFocusThreadComposer] = useState(false);
  // A search in the URL (a reload, a shared link) opens with its results shown.
  const [searchOpen, setSearchOpen] = useState(() => !!q);
  // A message of a thread to show once its panel has the thread; not kept in the URL, as `jump` is the channel's.
  const [threadJump, setThreadJump] = useState<string | null>(null);
  const [shortcutsOpen, setShortcutsOpen] = useState(false);

  const rootRef = useRef<HTMLDivElement>(null);
  const channelListRef = useRef<ChatMessageListHandle>(null);
  const channelComposerRef = useRef<ChatComposerHandle>(null);
  const threadListRef = useRef<ChatMessageListHandle>(null);
  const threadComposerRef = useRef<ChatComposerHandle>(null);

  // A reply, an edit or a thread draft belongs to the channel it was started in.
  const [stateChannelId, setStateChannelId] = useState(channelId);
  if (stateChannelId !== channelId) {
    setStateChannelId(channelId);
    setReplyTo(null);
    setEditing(null);
    setThreadDraft(null);
  }
  const lostOpenChannel = useCallback(() => {
    if (channelId) onChannelLost(channelId);
  }, [channelId, onChannelLost]);

  const onNewestShown = useCallback(() => clearNewMessageCount(null), [clearNewMessageCount]);
  const onJumpConsumed = useCallback(() => void setParams({ jump: null }), [setParams]);
  const cancelReply = useCallback(() => setReplyTo(null), []);
  const finishEdit = useCallback(() => setEditing(null), []);
  // A reply ends an edit in progress; an edit keeps the reply, which comes back with the draft.
  const replyInChannel = useCallback((message: ChatMessageResponse) => {
    setEditing(null);
    setReplyTo(message);
  }, []);
  const focusChannelComposer = useCallback(() => channelComposerRef.current?.focus(), []);
  const editLastInChannel = useCallback((): boolean => {
    const message = channelListRef.current?.lastOwnEditable();
    if (!message) return false;
    setEditing(message);
    return true;
  }, []);
  // Escape with nothing to cancel: the reader is done reading.
  const showNewestInChannel = useCallback((): boolean => {
    channelListRef.current?.showNewest();
    return true;
  }, []);

  // ── Threads ──

  const openThread = useCallback(
    (threadId: string, options?: { focus?: boolean }) => {
      setThreadDraft(null);
      setThreadJump(null);
      setFocusThreadComposer(!!options?.focus);
      void setParams({ thread: threadId });
    },
    [setParams],
  );

  // Replaced, not pushed: Back after closing goes where the reader came from, not into the thread again.
  const closeThread = useCallback(() => {
    setThreadDraft(null);
    setThreadJump(null);
    void setParams({ thread: null }, { history: "replace" });
  }, [setParams]);

  /**
   * Opens the thread of a message, or starts one: a message with a thread
   * opens it; any other message opens the draft with a title prefilled from
   * the message, and creating it opens the new thread, or the existing one
   * when someone else started it first (409). A message inside a thread
   * cannot start one.
   */
  const openThreadFor = useCallback(
    (message: ChatMessageResponse) => {
      if (message.thread) {
        openThread(message.thread.threadId);
        return;
      }
      if (message.threadId || message.deleted) return;
      setThreadDraft(message);
      if (thread) void setParams({ thread: null }, { history: "replace" });
    },
    [openThread, setParams, thread],
  );

  const jumpInChannel = useCallback((messageId: string) => void setParams({ jump: messageId }), [setParams]);

  /**
   * Shows a found or pinned message: a thread message in its thread's panel,
   * any other in the channel. In the daily updates channel the list first
   * switches to the message's day, and drops a person filter of another
   * author; in the pull requests channel it drops the status filter that
   * hides the message and a person filter of another author. Either way the
   * message is in the list it opens around. nuqs merges the updates of one
   * handler into one URL change.
   */
  const jumpToMessage = useCallback(
    (message: ChatMessageResponse) => {
      if (message.threadId) {
        openThread(message.threadId);
        setThreadJump(message.id);
        return;
      }
      if (updateFilter) void setUpdateFilters(updateFiltersFor(message, updateFilter));
      if (pullRequestFilter) {
        const changes = pullRequestFiltersFor(message, pullRequestFilter);
        if (changes) void setPullRequestFilters(changes);
      }
      jumpInChannel(message.id);
    },
    [jumpInChannel, openThread, updateFilter, setUpdateFilters, pullRequestFilter, setPullRequestFilters],
  );
  // The list of a past day holds no newer messages: its jump to the present shows today, for the same person.
  const showToday = useCallback(() => void setUpdateFilters({ day: null }), [setUpdateFilters]);
  const onThreadJumpConsumed = useCallback(() => setThreadJump(null), []);

  // A link to a thread message (`thread` and `threadMessage`): the panel shows it once, then the URL drops it.
  useEffect(() => {
    if (!threadMessage) return;
    setThreadJump(threadMessage);
    void setParams({ threadMessage: null }, { history: "replace" });
  }, [threadMessage, setParams]);

  const openSearch = useCallback(() => setSearchOpen(true), []);
  const openShortcuts = useCallback(() => setShortcutsOpen(true), []);
  useChatShortcuts({
    rootRef,
    channelList: channelListRef,
    channelComposer: channelComposerRef,
    threadList: threadListRef,
    threadComposer: threadComposerRef,
    onSearch: openSearch,
    onShortcuts: openShortcuts,
  });
  const onThreadNewestShown = useCallback(
    () => (thread ? clearNewMessageCount(thread) : undefined),
    [clearNewMessageCount, thread],
  );

  // A deleted thread root keeps only its summary, so the thread stays reachable.
  const renderFooter = useCallback(
    (message: ChatMessageResponse) => (
      <>
        {!message.deleted && <ChatReactions message={message} canReact={canWrite} />}
        {message.thread && (
          <ChatThreadSummary
            thread={message.thread}
            users={message.users}
            active={message.thread.threadId === thread}
            onOpen={openThread}
          />
        )}
      </>
    ),
    [canWrite, openThread, thread],
  );

  return (
    // Every write in the chat goes to the channel the reader can read, not to the URL's.
    <ChatReadableChannelProvider value={channel?.id}>
      {/*
        Focusable, so a click anywhere in the chat puts focus inside it and Mod+F opens its search;
        never a Tab stop, so it shows no focus ring.
      */}
      <div
        ref={rootRef}
        tabIndex={-1}
        className="bg-background flex h-full flex-col outline-none focus-visible:ring-0 focus-visible:ring-offset-0"
      >
        <ChatHeader
          orgId={orgId}
          channel={channel}
          controls={
            <>
              {channel && (
                <>
                  <ChatThreadList activeThreadId={thread} onOpen={openThread} />
                  <ChatPinsPopover onJump={jumpToMessage} />
                  <ChatMembersPopover channel={channel} />
                  <ChatSearchPanel open={searchOpen} onOpenChange={setSearchOpen} onJump={jumpToMessage} />
                </>
              )}
              <ChannelMenu channel={channel} onRemoving={onRemoving} onRemoveFailed={onRemoveFailed} />
              <ShortcutsButton onClick={openShortcuts} />
            </>
          }
        />
        <ChatConnectionBanner />
        {lostName && (
          <div
            role="status"
            className="border-border bg-muted/50 flex items-center gap-2 border-b px-4 py-2 text-sm lg:px-6"
          >
            <Lock className="text-muted-foreground h-4 w-4 shrink-0" aria-hidden />
            <span className="text-foreground min-w-0 flex-1">{tChannels("lost.notice", { name: lostName })}</span>
            <Button variant="ghost" size="icon-sm" aria-label={tChannels("lost.dismiss")} onClick={dismissLost}>
              <X className="h-4 w-4" />
            </Button>
          </div>
        )}
        {channelsQuery.isError ? (
          <div role="alert" className="flex flex-1 flex-col items-center justify-center p-6 text-center">
            <AlertCircle className="text-destructive h-8 w-8" />
            <p className="text-foreground mt-3 text-sm font-medium">{tChannels("loadError")}</p>
            {channelsQuery.error?.message && (
              <p className="text-muted-foreground mt-1 max-w-md text-xs">{channelsQuery.error.message}</p>
            )}
            <Button
              variant="outline"
              className="mt-4"
              disabled={channelsQuery.isFetching}
              onClick={() => void channelsQuery.refetch()}
            >
              {tChannels("retry")}
            </Button>
          </div>
        ) : noChannel ? (
          <EmptyState Icon={Hash} title={tChannels("none.title")} description={tChannels("none.description")} />
        ) : (
          <div className="flex min-h-0 flex-1">
            <div className="flex min-w-0 flex-1 flex-col">
              <ChatMessageActionsProvider
                onReply={replyInChannel}
                onEdit={setEditing}
                onOpenThread={openThreadFor}
                onFocusComposer={focusChannelComposer}
              >
                {/*
                  Keyed by channel: another channel opens at its own read position and scroll state.
                  Shown only for a channel the reader can read, so a link to any other channel fetches
                  nothing before the URL is replaced with a readable one.
                */}
                {channel ? (
                  <ChannelMessageList
                    key={
                      filter
                        ? [channel.id, filter.day, filter.authorId, filter.pullRequestStatuses.join(",")].join(":")
                        : channel.id
                    }
                    channelId={channel.id}
                    listRef={channelListRef}
                    label={t("logLabelNamed", { name: channel.name })}
                    filter={filter}
                    onShowToday={showToday}
                    jumpTo={listJump}
                    onJumpConsumed={onJumpConsumed}
                    newMessageCount={channelNewMessageCount}
                    onNewestShown={onNewestShown}
                    renderFooter={renderFooter}
                    onChannelLost={lostOpenChannel}
                  />
                ) : (
                  <div className="flex flex-1 flex-col items-center justify-center">
                    <Loader2 className="text-muted-foreground h-8 w-8 animate-spin" />
                    <p className="text-muted-foreground mt-3 text-sm">{tList("loading")}</p>
                  </div>
                )}
              </ChatMessageActionsProvider>
              {/* Below the list, so the newest message stays right above the composer. */}
              <ChatComposer
                ref={channelComposerRef}
                threadId={null}
                placeholder={
                  channel ? tComposer("placeholderChannel", { name: channel.name }) : tComposer("placeholder")
                }
                replyTo={replyTo}
                onCancelReply={cancelReply}
                editing={editing}
                onEditDone={finishEdit}
                onEditLast={editLastInChannel}
                onIdleEscape={showNewestInChannel}
                typingUserIds={typingUserIds(null)}
              />
            </div>
            <ChatThreadColumn open={!!thread || !!threadDraft} onClose={closeThread}>
              <ChatThreadPanel
                threadId={thread}
                draftRoot={threadDraft}
                focusComposer={focusThreadComposer}
                onClose={closeThread}
                onOpenThread={openThread}
                onJumpToChannelMessage={jumpInChannel}
                typingUserIds={thread ? typingUserIds(thread) : NO_TYPING}
                newMessageCount={threadNewMessageCount}
                onNewestShown={onThreadNewestShown}
                jumpTo={threadJump}
                onJumpConsumed={onThreadJumpConsumed}
                listRef={threadListRef}
                composerRef={threadComposerRef}
              />
            </ChatThreadColumn>
          </div>
        )}
        <ChatShortcutsDialog open={shortcutsOpen} onOpenChange={setShortcutsOpen} />
      </div>
    </ChatReadableChannelProvider>
  );
}

/** The day and person filters a jump to `message` needs: its day, and no person when it is another author's. */
function updateFiltersFor(
  message: ChatMessageResponse,
  filter: ChatChannelFilter,
): { day: string | null; person?: null } {
  const day = updateDayOf(message.createdAt);
  const changes: { day: string | null; person?: null } = { day: day === updateToday() ? null : day };
  if (filter.authorId && filter.authorId !== message.author?.userId) changes.person = null;
  return changes;
}

/**
 * The day and person the daily updates channel is narrowed to, from the URL;
 * null for any other channel. A `jump` in the URL (a message link) is held
 * back from the list until the message is read and the filters show its day
 * and author, replacing the URL entry, so the list opens around a message it
 * holds. A message that cannot be read is handed on, and the list reports it.
 */
function useUpdateChannelFilter(updatesChannel: boolean, jump: string | null) {
  const [filters, setUpdateFilters] = useUpdateFilterParams();
  const day = resolveUpdateDay(filters.day);
  // An empty `person` is no filter, as on the server page.
  const person = filters.person || null;
  // A stable object, so the list's query key does not change on every render.
  const filter = useMemo<ChatChannelFilter | null>(
    () => (updatesChannel ? { day, authorId: person, pullRequestStatuses: [] } : null),
    [updatesChannel, day, person],
  );

  const jumpQuery = useChatMessage(filter ? jump : null);
  const jumpMessage = jumpQuery.data;
  const jumpMatches =
    !!filter &&
    !!jumpMessage &&
    updateDayOf(jumpMessage.createdAt) === filter.day &&
    (!filter.authorId || filter.authorId === jumpMessage.author?.userId);
  useEffect(() => {
    if (!filter || !jumpMessage || jumpMatches) return;
    void setUpdateFilters(updateFiltersFor(jumpMessage, filter), { history: "replace" });
  }, [filter, jumpMessage, jumpMatches, setUpdateFilters]);

  // Settled: read and shown by the filters, unreadable (null), or failed.
  const jumpSettled = jumpMatches || jumpMessage === null || jumpQuery.isError;
  const listJump = filter && jump && !jumpSettled ? null : jump;
  return { filter, listJump, setUpdateFilters };
}

/**
 * The status and person filters a jump to `message` must drop for the
 * message to be listed: the statuses when it is no share with one of them,
 * the person when it is another author's. Null when the filters show it.
 */
function pullRequestFiltersFor(
  message: ChatMessageResponse,
  filter: ChatChannelFilter,
): { prStatuses?: null; person?: null } | null {
  const changes: { prStatuses?: null; person?: null } = {};
  const status = message.pullRequestShare?.status;
  if (filter.pullRequestStatuses.length > 0 && !(status && filter.pullRequestStatuses.includes(status))) {
    changes.prStatuses = null;
  }
  if (filter.authorId && filter.authorId !== message.author?.userId) changes.person = null;
  return changes.prStatuses === undefined && changes.person === undefined ? null : changes;
}

/**
 * The statuses and person the pull requests channel is narrowed to, from the
 * URL; null for any other channel and when neither is chosen, so the
 * unfiltered list keeps its key and the server's prefetch. The statuses are
 * resolved as the server page resolves them (known values, enum order), so
 * both build the same query key. A `jump` in the URL (a message link) is held
 * back from the list until the message is read and the filters show it; a
 * filter that hides it is cleared, replacing the URL entry. A message that
 * cannot be read is handed on, and the list reports it.
 */
function usePullRequestChannelFilter(pullRequestsChannel: boolean, jump: string | null) {
  const [filters, setPullRequestFilters] = usePullRequestChannelFilterParams();
  // A joined string, so the memo below sees an equal choice as unchanged.
  const statusesKey = resolvePullRequestStatuses(filters.prStatuses).join(",");
  // An empty `person` is no filter, as on the server page.
  const person = filters.person || null;
  // A stable object, so the list's query key does not change on every render.
  const filter = useMemo<ChatChannelFilter | null>(() => {
    if (!pullRequestsChannel || (!statusesKey && !person)) return null;
    return { day: null, authorId: person, pullRequestStatuses: resolvePullRequestStatuses(statusesKey) };
  }, [pullRequestsChannel, statusesKey, person]);

  const jumpQuery = useChatMessage(filter ? jump : null);
  const jumpMessage = jumpQuery.data;
  const jumpMatches = !!filter && !!jumpMessage && pullRequestFiltersFor(jumpMessage, filter) === null;
  useEffect(() => {
    if (!filter || !jumpMessage) return;
    const changes = pullRequestFiltersFor(jumpMessage, filter);
    if (changes) void setPullRequestFilters(changes, { history: "replace" });
  }, [filter, jumpMessage, setPullRequestFilters]);

  // Settled: read and shown by the filters, unreadable (null), or failed.
  const jumpSettled = jumpMatches || jumpMessage === null || jumpQuery.isError;
  const listJump = filter && jump && !jumpSettled ? null : jump;
  return { filter, listJump, setPullRequestFilters };
}

interface ChannelMessageListProps {
  channelId: string | undefined;
  /**
   * The day and person of the daily updates channel, or the statuses and
   * person of the pull requests channel; null for any other channel and for
   * an unfiltered pull requests channel.
   */
  filter: ChatChannelFilter | null;
  onShowToday: () => void;
  listRef: Ref<ChatMessageListHandle>;
  label: string;
  jumpTo: string | null;
  onJumpConsumed: () => void;
  newMessageCount: number;
  onNewestShown: () => void;
  renderFooter: (message: ChatMessageResponse) => ReactNode;
  onChannelLost: () => void;
}

/**
 * The open channel's messages, opened at the read position the reader found
 * in that channel. A list of the daily updates channel names its day in its
 * empty state and at its start; a filtered list of the pull requests channel
 * has no day, and its empty state points back to its filters.
 */
function ChannelMessageList({ channelId, listRef, filter, ...props }: ChannelMessageListProps) {
  const t = useTranslations("updates.filters");
  const tPullRequests = useTranslations("pullRequests.filters");
  const locale = useLocale();
  const { marker, clearMarker } = useChatUnreadMarker(channelId, null);
  const day = filter?.day ?? null;
  const dayLabel = useMemo(() => (day ? formatDateOnly(day, locale) : null), [day, locale]);
  // Only the daily updates channel filters by day, so a filter without one is the pull requests channel's.
  const emptyState = !filter ? undefined : day ? (
    <EmptyState Icon={CalendarDays} title={t("empty")} description={t("emptyDescription")} />
  ) : (
    <EmptyState Icon={GitPullRequest} title={tPullRequests("empty")} description={tPullRequests("emptyDescription")} />
  );
  return (
    <ChatMessageList
      ref={listRef}
      threadId={null}
      unreadMarker={marker}
      onSent={clearMarker}
      filter={filter}
      emptyState={emptyState}
      startLabel={dayLabel ? t("startOfDay", { day: dayLabel }) : undefined}
      {...props}
    />
  );
}

/**
 * The channel header's settings: create, change and delete a channel and
 * manage a private channel's members for OWNER and ADMIN; leaving a private
 * channel for any of its members. Hidden when the reader can do none of these.
 */
function ChannelMenu({
  channel,
  onRemoving,
  onRemoveFailed,
}: {
  channel: ChatChannelResponse | undefined;
  /** Called as the reader deletes or leaves the channel, whose next read is then expected to be 404. */
  onRemoving: (channelId: string) => void;
  /** Called when that delete or leave fails, so the channel's 404 is unexpected again. */
  onRemoveFailed: (channelId: string) => void;
}) {
  const t = useTranslations("chat.channels");
  const router = useRouter();
  const canManage = useCanManageChatChannels();
  const [dialog, setDialog] = useState<"create" | "change" | null>(null);
  const [membersOpen, setMembersOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [leaveOpen, setLeaveOpen] = useState(false);

  const isPrivate = channel?.visibility === ChatChannelVisibility.PRIVATE;
  const canLeave = isPrivate && !!channel?.member;
  if (!canManage && !canLeave) return null;

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="outline" size="icon-sm" className="border-border" aria-label={t("menu")} title={t("menu")}>
            <Settings2 className="h-3.5 w-3.5" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" className="w-52">
          {canManage && (
            <DropdownMenuItem onClick={() => setDialog("create")}>
              <Plus className="h-3.5 w-3.5" />
              {t("new")}
            </DropdownMenuItem>
          )}
          {canManage && channel && (
            <DropdownMenuItem onClick={() => setDialog("change")}>
              <Pencil className="h-3.5 w-3.5" />
              {t("change")}
            </DropdownMenuItem>
          )}
          {canManage && isPrivate && (
            <DropdownMenuItem onClick={() => setMembersOpen(true)}>
              <Users className="h-3.5 w-3.5" />
              {t("manageMembers")}
            </DropdownMenuItem>
          )}
          {channel && (canLeave || canManage) && <DropdownMenuSeparator />}
          {canLeave && (
            <DropdownMenuItem onClick={() => setLeaveOpen(true)}>
              <LogOut className="h-3.5 w-3.5" />
              {t("leaveChannel")}
            </DropdownMenuItem>
          )}
          {canManage && channel && (
            <DropdownMenuItem className="text-destructive focus:text-destructive" onClick={() => setDeleteOpen(true)}>
              <Trash2 className="h-3.5 w-3.5" />
              {t("deleteChannel")}
            </DropdownMenuItem>
          )}
        </DropdownMenuContent>
      </DropdownMenu>
      {canManage && (
        <>
          <ChatChannelDialog
            open={dialog !== null}
            onOpenChange={(open) => !open && setDialog(null)}
            channel={dialog === "change" ? channel : undefined}
            onCreated={(created) => router.push(chatChannelPath(created))}
          />
          <ChatChannelMembersDialog channel={membersOpen ? (channel ?? null) : null} onClose={() => setMembersOpen(false)} />
          <ChatChannelDeleteDialog
            channel={deleteOpen ? (channel ?? null) : null}
            onClose={() => setDeleteOpen(false)}
            onRemoving={onRemoving}
            onRemoveFailed={onRemoveFailed}
          />
        </>
      )}
      <ChatChannelLeaveDialog
        channel={leaveOpen ? (channel ?? null) : null}
        onClose={() => setLeaveOpen(false)}
        onRemoving={onRemoving}
        onRemoveFailed={onRemoveFailed}
      />
    </>
  );
}
