"use client";

import {
  useCallback,
  useEffect,
  useImperativeHandle,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type FocusEvent,
  type ReactNode,
  type Ref,
} from "react";
import { useQueryClient, type InfiniteData } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { AlertCircle, Loader2, Lock, MessagesSquare, X } from "lucide-react";
import { isApiError } from "@oba/shared/api/axios";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import type { UserAvatarUser } from "@/components/ui/user-avatar";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { updateDayOf, updateToday } from "@/features/updates/lib/update-day";
import { useSendChatMessage } from "../hooks/use-chat-mutations";
import {
  CHAT_KEYS,
  useChatMembers,
  useChatMeId,
  useChatMessages,
  useChatOrganizationId,
} from "../hooks/use-chat-messages";
import { useChatReadableChannelId } from "../hooks/use-chat-channels";
import { useMarkChatRead, useMarkReadOnVisible, type ChatUnreadMarker } from "../hooks/use-mark-read";
import type { ChatChannelFilter } from "../hooks/query-keys";
import { isChatEntryCard } from "../lib/chat-message-text";
import {
  CHAT_ERROR_CODES,
  CHAT_GROUP_WINDOW_MS,
  CHAT_PAGE_SIZE,
  CHAT_UNREAD_CAP,
  type ChatMessagePage,
  type ChatMessageResponse,
  type ChatOutboxMessage,
  type ChatReplyPreviewResponse,
  type ChatUserResponse,
} from "../schemas";
import { CHAT_ROW_ATTRIBUTE, ChatMessage, ChatOutboxRow, type ChatMessageSlot } from "./chat-message";
import { CHAT_MESSAGE_ROW_ATTRIBUTE } from "./chat-message-actions";
import { ChatJumpButton } from "./chat-jump-button";
import { chatReplyPreviewOf } from "./chat-reply-preview";

/** How far outside the visible area the next page starts loading. */
const LOAD_OLDER_MARGIN = "300px 0px 0px 0px";
const LOAD_NEWER_MARGIN = "0px 0px 300px 0px";
/** Within this distance of the bottom the reader is "at the bottom" and new messages scroll in. */
const AT_BOTTOM_PX = 48;
/**
 * Pages a list loads, counting back from the newest message, to find the
 * first unread one of a reader whose read message is unknown: enough for a
 * count just under the cap, and below `CHAT_MAX_PAGES`, so the newest page is
 * never dropped while looking.
 */
const SEEK_UNREAD_MAX_PAGES = Math.ceil(CHAT_UNREAD_CAP / CHAT_PAGE_SIZE) + 2;
/** How long a message reached by a jump stays highlighted. */
const FLASH_MS = 1600;
/**
 * How long the reader stays at the newest message, without a break, before
 * the unread bar hides: long enough that scrolling past the bottom does not
 * count as having read the new messages.
 */
const UNREAD_BAR_HIDE_DELAY_MS = 3000;

type ChatRow =
  | { type: "day"; key: string; label: string }
  | { type: "divider"; key: string }
  | { type: "message"; key: string; message: ChatMessageResponse; grouped: boolean }
  | {
      type: "outbox";
      key: string;
      row: ChatOutboxMessage;
      grouped: boolean;
      replyTo: ChatReplyPreviewResponse | null;
      replyUsers: Record<string, ChatUserResponse>;
    };

/** Where the list scrolls once the rows it needs are rendered. */
type ScrollTarget = { kind: "bottom" } | { kind: "divider" } | { kind: "message"; id: string };

interface ScrollSnapshot {
  /** The topmost visible row and its distance from the top of the scroll area. */
  rowId: string | null;
  offset: number;
  atBottom: boolean;
}

const DIVIDER_ROW_ID = "unread-divider";
const NO_IDS: ReadonlySet<string> = new Set();

/** What the conversation around the list (its composer, the chat's keys) asks of it. */
export interface ChatMessageListHandle {
  /** Shows the "New messages" divider, loading around it when needed; false when nothing is unread. */
  jumpToUnread: () => boolean;
  /** Marks the conversation read and shows its newest message. */
  showNewest: () => void;
  /** The reader's newest loaded message that can be edited (their own, not a daily update). */
  lastOwnEditable: () => ChatMessageResponse | null;
  /** Focuses the newest message that has actions; false when there is none. */
  focusNewest: () => boolean;
}

interface ChatMessageListProps {
  /** Null reads the channel. */
  threadId: string | null;
  /** Accessible name of the message log. */
  label: string;
  /** A message to show (a link with `jump`); `onJumpConsumed` is called once it is handled. */
  jumpTo?: string | null;
  onJumpConsumed?: () => void;
  /** The read position when the conversation opened; undefined while it loads, so the list waits to decide where it opens. */
  unreadMarker: ChatUnreadMarker | null | undefined;
  /** Called when the reader sends a message: the divider and the unread bar go away then. */
  onSent?: () => void;
  /** Messages of others that arrived while the list was not at its newest page, from the realtime hook. */
  newMessageCount: number;
  /** Called once the reader sees the newest messages, to reset `newMessageCount`. */
  onNewestShown: () => void;
  /** Per-message content below the files (reactions, thread summary). */
  renderFooter?: ChatMessageSlot;
  /** Shown in place of the list when the conversation has no messages; a default empty state otherwise. */
  emptyState?: ReactNode;
  /** Shown above the oldest message once nothing older is left; "Start of the channel" by default. */
  startLabel?: string;
  /** Narrows a channel list to a day, an author and share statuses; the caller keys the list by it, so each filter opens fresh. */
  filter?: ChatChannelFilter | null;
  /**
   * Called by the jump to the present on a list of a past day, which holds
   * no newer messages itself: the caller switches the day to today.
   */
  onShowToday?: () => void;
  /**
   * Called when the channel answers 404 `KAZ_NOT_015`: the reader lost it
   * (removed from a private channel, the channel made private or deleted).
   * The list shows a lost state in its place.
   */
  onChannelLost?: () => void;
  ref?: Ref<ChatMessageListHandle>;
}

function isSameDayGroupable(previous: ChatMessageResponse, current: ChatMessageResponse): boolean {
  if (previous.deleted || current.deleted) return false;
  if (isChatEntryCard(previous) || isChatEntryCard(current)) return false;
  if (current.replyTo) return false;
  if (!previous.author || previous.author.userId !== current.author?.userId) return false;
  return Date.parse(current.createdAt) - Date.parse(previous.createdAt) <= CHAT_GROUP_WINDOW_MS;
}

/**
 * True when the read message is unknown: before the reader's first read of
 * the channel, when the server counts from the join time, and in a thread,
 * whose counts carry no read message. The first unread message is then found
 * by counting back from the newest.
 */
function isSinceJoin(marker: ChatUnreadMarker): boolean {
  return !marker.lastReadMessageId && !marker.lastReadAt;
}

/**
 * The first unread message when the read message is unknown: the client
 * counts back `count` messages from the newest, taking only the messages the
 * server counts: live ones by someone else. Null when the newest messages are
 * not loaded, when fewer are loaded, or when the count is capped and the real
 * position is older than it says.
 */
function findFirstUnreadSinceJoin(
  messages: readonly ChatMessageResponse[],
  count: number,
  meId: string | undefined,
  atNewest: boolean,
): string | null {
  if (!atNewest || count <= 0 || count >= CHAT_UNREAD_CAP) return null;
  let remaining = count;
  for (let index = messages.length - 1; index >= 0; index--) {
    const message = messages[index];
    if (!message || message.deleted || !message.author || message.author.userId === meId) continue;
    remaining -= 1;
    if (remaining === 0) return message.id;
  }
  return null;
}

/**
 * The first message the reader has not read, by someone else: the divider
 * goes above it. Null when every loaded message is read, or when the read
 * message is older than the loaded ones and nothing loaded is newer than it.
 */
function findFirstUnread(
  messages: readonly ChatMessageResponse[],
  marker: ChatUnreadMarker,
  meId: string | undefined,
  startLoaded: boolean,
): string | null {
  let startIndex = 0;
  const readIndex = marker.lastReadMessageId
    ? messages.findIndex((message) => message.id === marker.lastReadMessageId)
    : -1;
  if (readIndex >= 0) {
    startIndex = readIndex + 1;
  } else if (marker.lastReadAt) {
    const lastReadTime = Date.parse(marker.lastReadAt);
    startIndex = messages.findIndex((message) => Date.parse(message.createdAt) > lastReadTime);
    if (startIndex < 0) return null;
    // The read message is older than the loaded ones only when nothing loaded is read.
    if (startIndex === 0 && !startLoaded) return null;
  } else {
    return null;
  }
  for (let index = startIndex; index < messages.length; index++) {
    const message = messages[index];
    if (message && message.author?.userId !== meId) return message.id;
  }
  return null;
}

function rowSelector(rowId: string): string {
  return `[${CHAT_ROW_ATTRIBUTE}="${CSS.escape(rowId)}"]`;
}

/**
 * The messages of the channel or a thread, read like a chat: the newest at
 * the bottom, older and newer pages loaded by keyset as the reader scrolls
 * either way, grouped by author and day, with the "New messages" divider,
 * the reader's unsent messages, and jumps to a quoted or linked message.
 *
 * The scroll area is a reversed flex column, so it opens at the bottom
 * before any script runs and a page inserted above keeps the view in place.
 * Every other change (a page dropped below by the page cap, a message that
 * arrives while the reader reads older ones) is compensated by keeping the
 * topmost visible row where it was.
 */
export function ChatMessageList({
  threadId,
  label,
  jumpTo,
  onJumpConsumed,
  unreadMarker,
  onSent,
  newMessageCount,
  onNewestShown,
  renderFooter,
  emptyState,
  startLabel,
  filter = null,
  onShowToday,
  onChannelLost,
  ref,
}: ChatMessageListProps) {
  const t = useTranslations("chat");
  const dates = useDateFormat();
  const queryClient = useQueryClient();
  const orgId = useChatOrganizationId();
  // Messages are loaded and marked read only in a channel the reader can read.
  const channelId = useChatReadableChannelId();
  const meId = useChatMeId();
  const { data: meResponse, isError: isMeError } = useGetMe();
  const { data: members } = useChatMembers({ enabled: false });
  const { retry, discard } = useSendChatMessage();
  const { mutate: markRead } = useMarkChatRead();

  const [anchor, setAnchor] = useState<string | null>(null);
  const anchorReasonRef = useRef<"jump" | "divider">("jump");
  const {
    data,
    messages,
    outbox,
    atNewest,
    error,
    isPending,
    isError,
    isRefetchError,
    isFetching,
    refetch,
    fetchPreviousPage,
    hasPreviousPage,
    isFetchingPreviousPage,
    isFetchPreviousPageError,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    isFetchNextPageError,
  } = useChatMessages({ channelId, threadId, anchor, filter });
  const pastDay = !threadId && !!filter && filter.day !== null && filter.day !== updateToday();

  const marker = unreadMarker ?? null;
  const startLoaded = !!data && !data.pages[0]?.hasOlder;
  const newestMessageId = messages[messages.length - 1]?.id;

  // ── Rows ────────────────────────────────────────────────────────────

  // Counted back from the newest message, the divider of a reader who never
  // read would move down with every message that arrives, so it is placed once.
  const [joinDivider, setJoinDivider] = useState<{ marker: ChatUnreadMarker; id: string } | null>(null);
  const sinceJoin = !!marker && isSinceJoin(marker);
  if (sinceJoin && marker && joinDivider?.marker !== marker) {
    const joinDividerId = findFirstUnreadSinceJoin(messages, marker.count, meId, atNewest);
    if (joinDividerId) setJoinDivider({ marker, id: joinDividerId });
  }

  const firstUnreadId = useMemo(() => {
    if (!marker) return null;
    if (sinceJoin) return joinDivider?.marker === marker ? joinDivider.id : null;
    return findFirstUnread(messages, marker, meId, startLoaded);
  }, [messages, marker, meId, startLoaded, sinceJoin, joinDivider]);

  const me: UserAvatarUser = useMemo(() => {
    const profile = meResponse?.data;
    return {
      firstName: profile?.profile?.firstName ?? undefined,
      lastName: profile?.profile?.lastName ?? undefined,
      email: profile?.email ?? undefined,
    };
  }, [meResponse]);

  const memberUsers = useMemo<Record<string, ChatUserResponse>>(() => {
    const users: Record<string, ChatUserResponse> = {};
    for (const member of members ?? []) {
      users[member.userId] = {
        userId: member.userId,
        firstName: member.firstName,
        lastName: member.lastName,
        email: member.email,
        member: true,
      };
    }
    return users;
  }, [members]);

  const unknownMember = t("message.unknownMember");
  const rows = useMemo<ChatRow[]>(() => {
    const result: ChatRow[] = [];
    let previousDay: string | null = null;
    let previous: ChatMessageResponse | null = null;

    const dayRow = (createdAt: string): boolean => {
      const day = dates.dayKey(createdAt);
      if (day === previousDay) return false;
      previousDay = day;
      const relative = dates.relativeDay(day);
      result.push({
        type: "day",
        key: `day-${day}`,
        label:
          relative === "today"
            ? t("list.today")
            : relative === "yesterday"
              ? t("list.yesterday")
              : dates.formatDay(createdAt),
      });
      return true;
    };

    for (const message of messages) {
      let breaksGroup = dayRow(message.createdAt);
      if (message.id === firstUnreadId) {
        result.push({ type: "divider", key: DIVIDER_ROW_ID });
        breaksGroup = true;
      }
      const grouped = !breaksGroup && !!previous && isSameDayGroupable(previous, message);
      result.push({ type: "message", key: message.id, message, grouped });
      previous = message;
    }

    if (atNewest) {
      let previousOutbox: ChatOutboxMessage | null = null;
      for (const row of outbox) {
        const breaksGroup = dayRow(row.createdAt);
        const lastMessage = previousOutbox ? null : previous;
        const grouped =
          !breaksGroup &&
          !row.replyToMessageId &&
          (previousOutbox
            ? Date.parse(row.createdAt) - Date.parse(previousOutbox.createdAt) <= CHAT_GROUP_WINDOW_MS
            : !!lastMessage &&
              !lastMessage.deleted &&
              !isChatEntryCard(lastMessage) &&
              !!meId &&
              lastMessage.author?.userId === meId &&
              Date.parse(row.createdAt) - Date.parse(lastMessage.createdAt) <= CHAT_GROUP_WINDOW_MS);
        const original = row.replyToMessageId
          ? messages.find((message) => message.id === row.replyToMessageId)
          : undefined;
        result.push({
          type: "outbox",
          key: `outbox-${row.clientNonce}`,
          row,
          grouped,
          replyTo: original ? chatReplyPreviewOf(original, unknownMember) : null,
          replyUsers: original?.users ?? {},
        });
        previousOutbox = row;
      }
    }
    return result;
  }, [messages, outbox, atNewest, firstUnreadId, dates, meId, t, unknownMember]);

  // ── Scrolling ───────────────────────────────────────────────────────

  const scrollRef = useRef<HTMLDivElement>(null);
  const olderSentinelRef = useRef<HTMLDivElement>(null);
  const newerSentinelRef = useRef<HTMLDivElement>(null);
  const [bottomSentinel, setBottomSentinel] = useState<HTMLDivElement | null>(null);
  const [logElement, setLogElement] = useState<HTMLDivElement | null>(null);

  const snapshotRef = useRef<ScrollSnapshot>({ rowId: null, offset: 0, atBottom: true });
  const targetRef = useRef<ScrollTarget | null>(null);
  const previousRef = useRef<{ atNewest: boolean; newestId: string | undefined; outboxCount: number }>({
    atNewest,
    newestId: newestMessageId,
    outboxCount: outbox.length,
  });
  const [atBottom, setAtBottom] = useState<boolean>(true);
  const [scrolledAway, setScrolledAway] = useState<boolean>(false);
  /** The newest row at least partly in view; the messages after it are below the view. */
  const [lastVisibleRowId, setLastVisibleRowId] = useState<string | null>(null);
  /** Messages of others that arrived at the newest page while the reader was scrolled up. */
  const [arrivedIds, setArrivedIds] = useState<ReadonlySet<string>>(NO_IDS);
  /** True once the reader reached the newest message or marked it read: the opening unread are seen. */
  const [unreadSeen, setUnreadSeen] = useState<boolean>(false);
  const [flashId, setFlashId] = useState<string | null>(null);

  const takeSnapshot = useCallback(() => {
    const container = scrollRef.current;
    if (!container) return;
    const distance = Math.abs(container.scrollTop);
    const containerRect = container.getBoundingClientRect();
    const rowElements = container.querySelectorAll<HTMLElement>(`[${CHAT_ROW_ATTRIBUTE}]`);
    let rowId: string | null = null;
    let offset = 0;
    for (const row of rowElements) {
      const rect = row.getBoundingClientRect();
      if (rect.bottom > containerRect.top) {
        rowId = row.getAttribute(CHAT_ROW_ATTRIBUTE);
        offset = rect.top - containerRect.top;
        break;
      }
    }
    let lastVisible: string | null = null;
    for (let index = rowElements.length - 1; index >= 0; index--) {
      const row = rowElements[index];
      if (row && row.getBoundingClientRect().top < containerRect.bottom) {
        lastVisible = row.getAttribute(CHAT_ROW_ATTRIBUTE);
        break;
      }
    }
    snapshotRef.current = { rowId, offset, atBottom: distance < AT_BOTTOM_PX };
    setAtBottom(distance < AT_BOTTOM_PX);
    // More than about one screen above the newest message.
    setScrolledAway(distance > container.clientHeight);
    setLastVisibleRowId(lastVisible);
  }, []);

  const frameRef = useRef<number | null>(null);
  const onScroll = useCallback(() => {
    if (frameRef.current !== null) return;
    frameRef.current = requestAnimationFrame(() => {
      frameRef.current = null;
      takeSnapshot();
    });
  }, [takeSnapshot]);
  useEffect(
    () => () => {
      if (frameRef.current !== null) cancelAnimationFrame(frameRef.current);
    },
    [],
  );

  const flash = useCallback((messageId: string) => {
    setFlashId(messageId);
  }, []);
  useEffect(() => {
    if (!flashId) return;
    const timer = setTimeout(() => setFlashId(null), FLASH_MS);
    return () => clearTimeout(timer);
  }, [flashId]);

  /**
   * Scrolls to the target if its row is rendered; true when it was. The
   * snapshot is retaken at once: a change of the rows before the scroll event
   * arrives would otherwise restore the position from before the scroll.
   */
  const scrollToTarget = useCallback(
    (target: ScrollTarget): boolean => {
      const container = scrollRef.current;
      if (!container) return false;
      if (target.kind === "bottom") {
        container.scrollTop = 0;
        takeSnapshot();
        return true;
      }
      const rowId = target.kind === "divider" ? DIVIDER_ROW_ID : target.id;
      const row = container.querySelector<HTMLElement>(rowSelector(rowId));
      if (!row) return false;
      row.scrollIntoView({ block: target.kind === "divider" ? "start" : "center" });
      if (target.kind === "message") flash(target.id);
      takeSnapshot();
      return true;
    },
    [flash, takeSnapshot],
  );

  // While the list loads older pages to find the first unread message, the
  // rows change before the divider is there; the target must survive that.
  const [seekingUnread, setSeekingUnread] = useState<boolean>(false);
  const seekingUnreadRef = useRef<boolean>(false);
  const updateSeekingUnread = useCallback((seeking: boolean) => {
    seekingUnreadRef.current = seeking;
    setSeekingUnread(seeking);
  }, []);

  // After every change of the rows: go to a pending target, follow new
  // messages at the bottom, or keep the reader's topmost row in place.
  useLayoutEffect(() => {
    const container = scrollRef.current;
    const previous = previousRef.current;
    previousRef.current = { atNewest, newestId: newestMessageId, outboxCount: outbox.length };
    if (!container) return;
    const snapshot = snapshotRef.current;

    const sent = atNewest && outbox.length > previous.outboxCount;
    if (sent) targetRef.current = { kind: "bottom" };

    const target = targetRef.current;
    if (target && !isPending) {
      if (scrollToTarget(target)) {
        targetRef.current = null;
        return;
      }
      // The divider was not rendered after all (its message is the reader's own): open at the bottom.
      if (target.kind === "divider" && data && !seekingUnreadRef.current) {
        targetRef.current = null;
        container.scrollTop = 0;
        takeSnapshot();
        return;
      }
    }

    if (snapshot.atBottom && previous.atNewest && atNewest) {
      container.scrollTop = 0;
    } else if (snapshot.rowId) {
      const row = container.querySelector<HTMLElement>(rowSelector(snapshot.rowId));
      if (row) {
        const offset = row.getBoundingClientRect().top - container.getBoundingClientRect().top;
        container.scrollTop += offset - snapshot.offset;
      }
      // Messages of others that arrived below while the reader reads older ones.
      if (atNewest && previous.atNewest && previous.newestId && newestMessageId !== previous.newestId) {
        const previousIndex = messages.findIndex((message) => message.id === previous.newestId);
        if (previousIndex >= 0) {
          const arrived = messages
            .slice(previousIndex + 1)
            .filter((message) => message.author?.userId !== meId)
            .map((message) => message.id);
          if (arrived.length > 0) setArrivedIds((ids) => new Set([...ids, ...arrived]));
        }
      }
    }
    takeSnapshot();
  }, [rows]); // eslint-disable-line react-hooks/exhaustive-deps -- reason: the scroll position is corrected once per change of the rendered rows; the other values are read as of that change

  // A row that grows or shrinks without a change of the rows (an image that
  // loads, a card that expands) would move the view, because the reversed
  // column keeps the bottom in place. Away from the bottom the reader's
  // topmost row stays where it was instead, as it does for a change of rows.
  useEffect(() => {
    const container = scrollRef.current;
    if (!container || !logElement) return;
    const observer = new ResizeObserver(() => {
      const snapshot = snapshotRef.current;
      if (snapshot.atBottom || !snapshot.rowId || targetRef.current) return;
      const row = container.querySelector<HTMLElement>(rowSelector(snapshot.rowId));
      if (!row) return;
      const shift = row.getBoundingClientRect().top - container.getBoundingClientRect().top - snapshot.offset;
      if (Math.abs(shift) >= 1) container.scrollTop += shift;
    });
    observer.observe(logElement);
    return () => observer.disconnect();
  }, [logElement]);

  const outboxCountRef = useRef<number>(outbox.length);
  useEffect(() => {
    if (outbox.length > outboxCountRef.current) onSent?.();
    outboxCountRef.current = outbox.length;
  }, [outbox.length, onSent]);

  // ── Jumps ───────────────────────────────────────────────────────────

  // Read through a ref, so the jump handler every row receives stays the same
  // object and a new message does not re-render every row.
  const messagesRef = useRef<readonly ChatMessageResponse[]>(messages);
  useLayoutEffect(() => {
    messagesRef.current = messages;
  }, [messages]);

  const jumpToMessage = useCallback(
    (messageId: string) => {
      if (messagesRef.current.some((message) => message.id === messageId)) {
        scrollToTarget({ kind: "message", id: messageId });
        return;
      }
      anchorReasonRef.current = "jump";
      targetRef.current = { kind: "message", id: messageId };
      setAnchor(messageId);
    },
    [scrollToTarget],
  );

  /**
   * Goes to the newest message: a smooth scroll when the newest page is
   * loaded; otherwise the list switches to the newest page, or starts it over
   * when the page cap dropped it, and lands at its bottom without passing
   * through the pages in between.
   */
  const jumpToNewest = useCallback(() => {
    targetRef.current = { kind: "bottom" };
    setArrivedIds(NO_IDS);
    if (pastDay) {
      onShowToday?.();
      return;
    }
    if (!orgId || !channelId) return;
    const newestKey = threadId
      ? CHAT_KEYS.thread(orgId, threadId)
      : CHAT_KEYS.channel(orgId, channelId, null, filter);
    const cached = queryClient.getQueryData<InfiniteData<ChatMessagePage, unknown>>(newestKey);
    const cachedLast = cached?.pages[cached.pages.length - 1];
    // The page cap dropped the newest page while the reader read older ones: start the list over.
    if (cachedLast?.hasNewer) void queryClient.resetQueries({ queryKey: newestKey, exact: true });
    if (anchor) {
      setAnchor(null);
    } else if (!cachedLast?.hasNewer) {
      scrollRef.current?.scrollTo({ top: 0, behavior: "smooth" });
      targetRef.current = null;
    }
  }, [anchor, orgId, channelId, threadId, queryClient, pastDay, onShowToday, filter]);

  // A message named in the URL.
  useEffect(() => {
    if (!jumpTo || isPending) return;
    jumpToMessage(jumpTo);
    onJumpConsumed?.();
  }, [jumpTo, isPending]); // eslint-disable-line react-hooks/exhaustive-deps -- reason: a URL jump is handled once per value, after the first page is in

  // A jump target that no longer exists (deleted, or not readable here).
  const anchorMissing = !!anchor && isError && isApiError(error, CHAT_ERROR_CODES.messageNotFound);
  useEffect(() => {
    if (!anchorMissing) return;
    if (anchorReasonRef.current === "jump") toast.error(t("list.jumpNotFound"));
    targetRef.current = { kind: "bottom" };
    setAnchor(null);
  }, [anchorMissing, t]);

  // Only the channel's own 404 counts: a stale organization is a 404 too (`ORG_NOT_002`), and is not a lost channel.
  const channelLost = isError && isApiError(error, CHAT_ERROR_CODES.channelNotFound);
  useEffect(() => {
    if (channelLost) onChannelLost?.();
  }, [channelLost, onChannelLost]);

  // Where the list opens, decided once the first page, the read position and
  // the reader are known: at the "New messages" divider, near the top of the
  // view, whenever something is unread; otherwise at the bottom, where the
  // reversed column already is. A layout effect, so the bottom is never
  // painted first. A divider outside the newest page is reached by loading
  // around the last read message, or, when the read message is unknown, by
  // loading older pages until the counted-back message is in.
  const openedRef = useRef<boolean>(false);
  const [opened, setOpened] = useState<boolean>(false);
  const meKnown = !!meResponse || isMeError;
  useLayoutEffect(() => {
    if (openedRef.current || isPending || !data || unreadMarker === undefined || !meKnown) return;
    openedRef.current = true;
    setOpened(true);
    // A jump goes elsewhere, and a reader who already scrolled stays where they are.
    if (!marker || marker.count === 0 || jumpTo || anchor || !snapshotRef.current.atBottom) return;
    if (firstUnreadId) {
      scrollToTarget({ kind: "divider" });
      return;
    }
    if (isSinceJoin(marker)) {
      if (atNewest && marker.count < CHAT_UNREAD_CAP && hasPreviousPage) {
        targetRef.current = { kind: "divider" };
        updateSeekingUnread(true);
      }
      return;
    }
    const oldestLoaded = messages[0];
    // A read message before the list's day, or with a person or statuses
    // chosen, is outside the list: loading around it would not show the
    // divider.
    const readOutsideFilter =
      !!filter &&
      !!marker.lastReadAt &&
      (!!filter.authorId ||
        (filter.pullRequestStatuses?.length ?? 0) > 0 ||
        (filter.day !== null && updateDayOf(marker.lastReadAt) < filter.day));
    if (
      marker.lastReadMessageId &&
      marker.lastReadAt &&
      !readOutsideFilter &&
      !startLoaded &&
      oldestLoaded &&
      Date.parse(oldestLoaded.createdAt) > Date.parse(marker.lastReadAt)
    ) {
      anchorReasonRef.current = "divider";
      targetRef.current = { kind: "divider" };
      setAnchor(marker.lastReadMessageId);
    }
  }, [isPending, data, unreadMarker, meKnown]); // eslint-disable-line react-hooks/exhaustive-deps -- reason: where the list opens is decided once, when everything it depends on is in

  // Loads older pages until the counted-back first unread message is loaded.
  // Not found within the limit, the list stays at the bottom.
  useEffect(() => {
    if (!seekingUnread) return;
    const pageCount = data?.pages.length ?? 0;
    if (firstUnreadId || !hasPreviousPage || isFetchPreviousPageError || pageCount >= SEEK_UNREAD_MAX_PAGES) {
      // Once found, the change of rows has already scrolled to the divider.
      if (!firstUnreadId && targetRef.current?.kind === "divider") targetRef.current = null;
      updateSeekingUnread(false);
      return;
    }
    if (!isFetchingPreviousPage) void fetchPreviousPage();
  }, [
    seekingUnread,
    firstUnreadId,
    hasPreviousPage,
    isFetchingPreviousPage,
    isFetchPreviousPageError,
    data,
    fetchPreviousPage,
    updateSeekingUnread,
  ]);

  // At the bottom the arrivals are seen; at the newest message the opening unread are too.
  useEffect(() => {
    if (!atBottom || !opened || seekingUnread) return;
    setArrivedIds(NO_IDS);
    if (!atNewest) return;
    setUnreadSeen(true);
    onNewestShown();
  }, [atBottom, atNewest, opened, seekingUnread, onNewestShown]);

  // ── Paging ──────────────────────────────────────────────────────────

  const canLoadOlder = !!hasPreviousPage && !isFetchingPreviousPage && !isFetchPreviousPageError;
  useEffect(() => {
    const root = scrollRef.current;
    const sentinel = olderSentinelRef.current;
    if (!root || !sentinel || !canLoadOlder) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) void fetchPreviousPage();
      },
      { root, rootMargin: LOAD_OLDER_MARGIN },
    );
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [canLoadOlder, fetchPreviousPage, messages.length]);

  const canLoadNewer = !!hasNextPage && !isFetchingNextPage && !isFetchNextPageError;
  useEffect(() => {
    const root = scrollRef.current;
    const sentinel = newerSentinelRef.current;
    if (!root || !sentinel || !canLoadNewer) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) void fetchNextPage();
      },
      { root, rootMargin: LOAD_NEWER_MARGIN },
    );
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [canLoadNewer, fetchNextPage, messages.length]);

  useMarkReadOnVisible({
    channelId,
    threadId,
    newestMessageId,
    sentinel: bottomSentinel,
    enabled: atNewest && !isPending,
  });

  // ── Unread bar ──────────────────────────────────────────────────────

  const [unreadBarDismissed, setUnreadBarDismissed] = useState<boolean>(false);
  const showUnreadBar = !!marker && marker.count > 0 && !unreadBarDismissed;
  // The bar hides once the newest message has been in view for the delay
  // without a break; the divider keeps marking where the unread began.
  const newestInView = atBottom && atNewest && opened && !seekingUnread;
  useEffect(() => {
    if (!showUnreadBar || !newestInView) return;
    const timer = setTimeout(() => setUnreadBarDismissed(true), UNREAD_BAR_HIDE_DELAY_MS);
    return () => clearTimeout(timer);
  }, [showUnreadBar, newestInView]);
  const markAllRead = () => {
    setUnreadBarDismissed(true);
    setUnreadSeen(true);
    if (atNewest && newestMessageId && channelId) {
      markRead({ channelId, threadId, lastReadMessageId: newestMessageId });
    } else {
      jumpToNewest();
    }
  };

  // ── Jump to present ─────────────────────────────────────────────────

  // Unseen messages below the view: the unread ones the conversation opened
  // with, until the reader reaches the newest message, and those that arrived
  // since. Loaded ones count only below the view; without the newest page,
  // the unread not loaded and the arrivals the realtime hook counted are added.
  const unreadActive = !!marker && marker.count > 0 && !unreadSeen;
  const newBelowCount = useMemo(() => {
    const dividerIndex = firstUnreadId ? messages.findIndex((message) => message.id === firstUnreadId) : -1;
    let seenIndex = -1;
    if (lastVisibleRowId === DIVIDER_ROW_ID) {
      seenIndex = dividerIndex - 1;
    } else if (lastVisibleRowId) {
      const visibleIndex = messages.findIndex((message) => message.id === lastVisibleRowId);
      // An unsent row in view: everything loaded is in view.
      seenIndex = visibleIndex >= 0 ? visibleIndex : messages.length - 1;
    }
    let loadedBelow = 0;
    let loadedUnread = 0;
    messages.forEach((message, index) => {
      if (message.deleted || !message.author || message.author.userId === meId) return;
      const unread = unreadActive && dividerIndex >= 0 && index >= dividerIndex;
      if (unread) loadedUnread += 1;
      if (index > seenIndex && (unread || arrivedIds.has(message.id))) loadedBelow += 1;
    });
    if (atNewest) return loadedBelow;
    const unloadedUnread = unreadActive && marker ? Math.max(0, marker.count - loadedUnread) : 0;
    return loadedBelow + unloadedUnread + newMessageCount;
  }, [messages, firstUnreadId, lastVisibleRowId, meId, unreadActive, arrivedIds, atNewest, marker, newMessageCount]);

  const showJumpButton = opened && !isPending && !!data && (!atNewest || scrolledAway || newBelowCount > 0);

  // ── Handle ──────────────────────────────────────────────────────────

  const focusNewest = useCallback((): boolean => {
    const rowElements = scrollRef.current?.querySelectorAll<HTMLElement>(`[${CHAT_MESSAGE_ROW_ATTRIBUTE}]`);
    const newest = rowElements?.[rowElements.length - 1];
    if (!newest) return false;
    newest.focus();
    return true;
  }, []);

  useImperativeHandle(
    ref,
    () => ({
      jumpToUnread: () => {
        if (scrollToTarget({ kind: "divider" })) return true;
        // The first unread message is older than the loaded pages: load around the last read one.
        if (!marker?.lastReadMessageId || marker.count === 0) return false;
        anchorReasonRef.current = "divider";
        targetRef.current = { kind: "divider" };
        setAnchor(marker.lastReadMessageId);
        return true;
      },
      showNewest: () => {
        setUnreadBarDismissed(true);
        if (atNewest && newestMessageId && channelId) {
          markRead({ channelId, threadId, lastReadMessageId: newestMessageId });
          scrollRef.current?.scrollTo({ top: 0, behavior: "smooth" });
        } else {
          jumpToNewest();
        }
      },
      lastOwnEditable: () => {
        if (!meId) return null;
        const loaded = messagesRef.current;
        for (let index = loaded.length - 1; index >= 0; index--) {
          const message = loaded[index];
          if (message && !message.deleted && !isChatEntryCard(message) && message.author?.userId === meId) {
            return message;
          }
        }
        return null;
      },
      focusNewest,
    }),
    [scrollToTarget, marker, atNewest, newestMessageId, markRead, threadId, jumpToNewest, meId, focusNewest],
  );

  /** Tab into the list lands on the newest message, not on the oldest button of the loaded pages. */
  const onScrollAreaFocus = (event: FocusEvent<HTMLDivElement>) => {
    if (event.target !== event.currentTarget || !event.currentTarget.matches(":focus-visible")) return;
    if (event.relatedTarget instanceof Node && event.currentTarget.contains(event.relatedTarget)) return;
    focusNewest();
  };

  const onRetry = useCallback((clientNonce: string) => retry(clientNonce), [retry]);
  const onDiscard = useCallback((clientNonce: string) => discard(clientNonce), [discard]);

  // ── Render ──────────────────────────────────────────────────────────

  let body: ReactNode;
  if (isPending) {
    body = (
      <div className="flex flex-1 flex-col items-center justify-center">
        <Loader2 className="text-muted-foreground h-8 w-8 animate-spin" />
        <p className="text-muted-foreground mt-3 text-sm">{t("list.loading")}</p>
      </div>
    );
  } else if (channelLost && !data) {
    body = (
      <div className="flex flex-1 flex-col">
        <EmptyState Icon={Lock} title={t("channels.lost.title")} description={t("channels.lost.description")} />
      </div>
    );
  } else if (isError && !data) {
    body = (
      <div className="flex flex-1 flex-col items-center justify-center p-6 text-center">
        <AlertCircle className="text-destructive h-8 w-8" />
        <p className="text-foreground mt-3 text-sm font-medium">{t("list.loadError")}</p>
        {error?.message && <p className="text-muted-foreground mt-1 max-w-md text-xs">{error.message}</p>}
        <Button variant="outline" className="mt-4" disabled={isFetching} onClick={() => void refetch()}>
          {t("list.retry")}
        </Button>
      </div>
    );
  } else if (messages.length === 0 && outbox.length === 0 && startLoaded) {
    body = (
      <div className="flex flex-1 flex-col">
        {emptyState ?? (
          <EmptyState
            Icon={MessagesSquare}
            title={t("list.empty.title")}
            description={t("list.empty.description")}
          />
        )}
      </div>
    );
  } else {
    body = (
      <div ref={setLogElement} role="log" aria-live="polite" aria-label={label} className="w-full py-4">
        <div
          ref={olderSentinelRef}
          className="flex min-h-8 items-center justify-center gap-2 px-4 text-xs"
        >
          {isFetchPreviousPageError ? (
            <>
              <AlertCircle className="text-destructive h-4 w-4 shrink-0" />
              <span className="text-foreground">{t("list.loadOlderError")}</span>
              <Button variant="outline" size="sm" className="h-7" onClick={() => void fetchPreviousPage()}>
                {t("list.retry")}
              </Button>
            </>
          ) : isFetchingPreviousPage ? (
            <>
              <Loader2 className="text-muted-foreground h-4 w-4 animate-spin" />
              <span className="text-muted-foreground">{t("list.loadingOlder")}</span>
            </>
          ) : startLoaded ? (
            <span className="text-muted-foreground">{startLabel ?? t("list.startOfChannel")}</span>
          ) : null}
        </div>

        {rows.map((row) => {
          switch (row.type) {
            case "day":
              return (
                <div key={row.key} role="separator" className="my-4 flex items-center gap-3 px-4 lg:px-6">
                  <div className="border-border flex-1 border-t border-dashed" />
                  <span className="kazan-grid-label inline-flex items-center gap-1.5" suppressHydrationWarning>
                    {row.label}
                  </span>
                  <div className="border-border flex-1 border-t border-dashed" />
                </div>
              );
            case "divider":
              return (
                <div
                  key={row.key}
                  {...{ [CHAT_ROW_ATTRIBUTE]: DIVIDER_ROW_ID }}
                  role="separator"
                  className="my-2 flex scroll-mt-3 items-center gap-2 px-4 lg:px-6"
                >
                  <div className="border-destructive flex-1 border-t border-dashed" />
                  <span className="text-destructive font-mono text-[11px] font-semibold tracking-[0.14em] uppercase">
                    {t("list.newMessagesDivider")}
                  </span>
                </div>
              );
            case "message":
              return (
                <ChatMessage
                  key={row.key}
                  message={row.message}
                  meId={meId}
                  dates={dates}
                  grouped={row.grouped}
                  highlighted={flashId === row.message.id}
                  onJumpTo={jumpToMessage}
                  renderFooter={renderFooter}
                />
              );
            case "outbox":
              return (
                <ChatOutboxRow
                  key={row.key}
                  row={row.row}
                  author={me}
                  meId={meId}
                  users={memberUsers}
                  dates={dates}
                  grouped={row.grouped}
                  replyTo={row.replyTo}
                  replyUsers={row.replyUsers}
                  onJumpTo={jumpToMessage}
                  onRetry={onRetry}
                  onDiscard={onDiscard}
                />
              );
          }
        })}

        <div ref={newerSentinelRef} className="flex min-h-2 items-center justify-center gap-2 px-4 text-xs">
          {isFetchNextPageError ? (
            <>
              <AlertCircle className="text-destructive h-4 w-4 shrink-0" />
              <span className="text-foreground">{t("list.loadNewerError")}</span>
              <Button variant="outline" size="sm" className="h-7" onClick={() => void fetchNextPage()}>
                {t("list.retry")}
              </Button>
            </>
          ) : isFetchingNextPage ? (
            <>
              <Loader2 className="text-muted-foreground h-4 w-4 animate-spin" />
              <span className="text-muted-foreground">{t("list.loadingNewer")}</span>
            </>
          ) : null}
        </div>
        <div ref={setBottomSentinel} className="h-px" aria-hidden />
      </div>
    );
  }

  return (
    <div className="relative flex min-h-0 flex-1 flex-col">
      {showUnreadBar && marker && (
        <div className="bg-primary text-primary-foreground flex items-center gap-2 px-4 py-1 text-xs lg:px-6">
          <span className="min-w-0 flex-1 truncate" suppressHydrationWarning>
            {marker.count >= CHAT_UNREAD_CAP
              ? t("list.unreadBarCapped", { count: CHAT_UNREAD_CAP })
              : t("list.unreadBar", { count: marker.count })}
            {marker.lastReadAt &&
              ` ${t("list.unreadSince", { time: dates.formatTime(marker.lastReadAt) })}`}
          </span>
          <button
            type="button"
            onClick={markAllRead}
            className="focus-visible:ring-ring shrink-0 rounded-sm font-semibold hover:underline focus-visible:ring-2 focus-visible:outline-none"
          >
            {t("list.markAsRead")}
          </button>
          <button
            type="button"
            onClick={() => setUnreadBarDismissed(true)}
            aria-label={t("list.dismiss")}
            className="focus-visible:ring-ring shrink-0 rounded-sm p-0.5 focus-visible:ring-2 focus-visible:outline-none"
          >
            <X className="h-3.5 w-3.5" />
          </button>
        </div>
      )}

      {isRefetchError && (
        <div
          role="alert"
          className="border-border bg-destructive/5 flex items-center gap-2 border-b px-4 py-2 text-sm lg:px-6"
        >
          <AlertCircle className="text-destructive h-4 w-4 shrink-0" />
          <span className="text-foreground min-w-0 flex-1">
            {t("list.refreshError")}
            {error?.message && <span className="text-muted-foreground ml-1 text-xs">{error.message}</span>}
          </span>
          <Button variant="outline" size="sm" className="h-7" disabled={isFetching} onClick={() => void refetch()}>
            {t("list.retry")}
          </Button>
        </div>
      )}

      <div
        ref={scrollRef}
        onScroll={onScroll}
        tabIndex={0}
        onFocus={onScrollAreaFocus}
        // No sideways overscroll: a horizontal swipe over a message is a reply, not a page gesture.
        // No focus ring: a click focuses the list too, and some browsers count that as visible focus;
        // keyboard focus moves on to the newest message, which shows its own.
        className="bg-card flex flex-1 flex-col-reverse overflow-y-auto overscroll-x-none outline-none [overflow-anchor:none] focus-visible:ring-0 focus-visible:ring-offset-0"
      >
        {body}
      </div>

      {showJumpButton && <ChatJumpButton newCount={newBelowCount} onClick={jumpToNewest} />}
    </div>
  );
}
