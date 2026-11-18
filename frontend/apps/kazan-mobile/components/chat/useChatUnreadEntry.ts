import * as React from "react";
import type { FlatList, NativeScrollEvent, NativeSyntheticEvent } from "react-native";

import {
  findChatFirstUnread,
  findChatFirstUnreadSinceJoin,
  isChatUnreadSinceJoin,
  type ChatListItem,
  type ChatUnreadMarker,
} from "@/components/chat/chatListModel";
import {
  CHAT_BOTTOM_START,
  chatBottomAfterContentSize,
  chatBottomAfterNewerAdded,
  chatBottomAfterNewerSettled,
  chatBottomAfterScroll,
  isChatBottomInView,
  type ChatBottomTrack,
} from "@/components/chat/chatBottomModel";
import {
  chatAutoscrollThreshold,
  chatUnreadOpenIndex,
  chatUnreadOpenStep,
  chatUnreadPhaseOf,
  chatUnreadReadMessageGoneStep,
  chatUnreadSeekStep,
  findChatMessageNearReadTime,
  isChatNewerPageAddedInPlace,
  isChatUnreadStateFailed,
  type ChatUnreadOpenPhase,
  type ChatUnreadOpenStep,
  type ChatUnreadSeekStep,
} from "@/components/chat/chatUnreadModel";
import { useChatUnread } from "@/hooks/queries/chat";
import { listChannelDayMessages } from "@/hooks/queries/chatApi";
import { useMarkChatRead } from "@/hooks/queries/chatMutations";
import { isApiError } from "@/lib/api";
import { CHAT_ERROR_CODES, type ChatMessageResponse } from "@/lib/schemas/chat";

/** Rough row height, to scroll near a row the list has not measured yet. */
const ESTIMATED_ROW_PX = 72;

/**
 * How long the reader stays at the newest message, without a break, before
 * the unread bar hides: long enough that scrolling past the bottom does not
 * count as having read the new messages.
 */
const UNREAD_BAR_HIDE_DELAY_MS = 3000;

/**
 * In an inverted list a view position of 1 puts the row at the top of the
 * screen, so the unread messages below the divider fill the view.
 */
export const UNREAD_DIVIDER_VIEW_POSITION = 1;

export type ChatListRef = React.RefObject<FlatList<ChatListItem> | null>;

/**
 * Scrolls a message list to a row. A row the list has not measured yet is
 * approached by an estimated offset first and aimed at again once rendered.
 */
export function useScrollToChatRow(listRef: ChatListRef) {
  const viewPosition = React.useRef<number>(0.5);

  const scrollToRow = React.useCallback(
    (index: number, position: number, animated: boolean) => {
      viewPosition.current = position;
      listRef.current?.scrollToIndex({ index, animated, viewPosition: position });
    },
    [listRef]
  );

  const onScrollToIndexFailed = React.useCallback(
    (info: { index: number; averageItemLength: number }) => {
      const length: number = info.averageItemLength || ESTIMATED_ROW_PX;
      listRef.current?.scrollToOffset({ offset: info.index * length, animated: false });
      setTimeout(() => {
        listRef.current?.scrollToIndex({
          index: info.index,
          animated: true,
          viewPosition: viewPosition.current,
        });
      }, 100);
    },
    [listRef]
  );

  return { scrollToRow, onScrollToIndexFailed };
}

export interface UseChatFirstUnreadIdOptions {
  messages: readonly ChatMessageResponse[];
  marker: ChatUnreadMarker;
  meId: string | undefined;
  /** The newest page is loaded. */
  atNewest: boolean;
  /** The oldest message of the conversation is loaded. */
  startLoaded: boolean;
}

/**
 * The message the "New messages" divider goes above. Counted back from the
 * newest message, it is found once per opening, so messages that arrive
 * later do not move it.
 */
export function useChatFirstUnreadId({
  messages,
  marker,
  meId,
  atNewest,
  startLoaded,
}: UseChatFirstUnreadIdOptions): string | null {
  const sinceJoin: boolean = marker.count > 0 && isChatUnreadSinceJoin(marker);
  const [joinDivider, setJoinDivider] = React.useState<{
    marker: ChatUnreadMarker;
    id: string;
  } | null>(null);
  if (sinceJoin && joinDivider?.marker !== marker) {
    const id: string | null = findChatFirstUnreadSinceJoin(
      messages,
      marker.count,
      meId,
      atNewest,
      startLoaded
    );
    if (id) setJoinDivider({ marker, id });
  }
  return React.useMemo(() => {
    if (marker.count === 0) return null;
    if (sinceJoin) return joinDivider?.marker === marker ? joinDivider.id : null;
    return findChatFirstUnread(messages, marker, meId, startLoaded);
  }, [messages, marker, meId, startLoaded, sinceJoin, joinDivider]);
}

/**
 * How long a scroll to the divider has to land before the list reads where
 * the reader is: until then the list still holds the position it opened
 * with, the bottom.
 */
const DIVIDER_SETTLE_MS = 300;

/**
 * Whether the bottom of an inverted message list, its newest message, is on
 * screen (`chatBottomModel`). It is read from every scroll event (`onScroll`,
 * which also calls `onReachBottom` there), again when a drag or a fling ends
 * (`onScrollEnd`), so the place a scroll comes to rest always counts, and when
 * the content height changes (`onContentSizeChange`), so a newer page added
 * below the reader counts by its height without another scroll.
 * `onNewerAddedInPlace` tells it such a page was added. The page stops being
 * pending one frame after it was committed or after its height was reported,
 * whichever is later: React Native lays a commit out before its layout effects
 * run, so the height event is already queued by then, and the frame lets a
 * scroll event measured before the page arrive while it is still recognised
 * by its old content height.
 */
export function useChatAtBottom(onReachBottom: () => void) {
  const track = React.useRef<ChatBottomTrack>(CHAT_BOTTOM_START);
  const [atBottom, setAtBottom] = React.useState<boolean>(true);
  const apply = React.useCallback((next: ChatBottomTrack): boolean => {
    track.current = next;
    const bottom: boolean = isChatBottomInView(next);
    setAtBottom(bottom);
    return bottom;
  }, []);
  const settleFrame = React.useRef<number | null>(null);
  const settleNewerNextFrame = React.useCallback(() => {
    if (settleFrame.current !== null) cancelAnimationFrame(settleFrame.current);
    settleFrame.current = requestAnimationFrame(() => {
      settleFrame.current = null;
      apply(chatBottomAfterNewerSettled(track.current));
    });
  }, [apply]);
  React.useEffect(
    () => () => {
      if (settleFrame.current !== null) cancelAnimationFrame(settleFrame.current);
    },
    []
  );
  const onScroll = React.useCallback(
    (event: NativeSyntheticEvent<NativeScrollEvent>) => {
      const { contentOffset, contentSize } = event.nativeEvent;
      if (apply(chatBottomAfterScroll(track.current, contentOffset.y, contentSize.height))) {
        onReachBottom();
      }
    },
    [apply, onReachBottom]
  );
  const onScrollEnd = React.useCallback(
    (event: NativeSyntheticEvent<NativeScrollEvent>) => {
      const { contentOffset, contentSize } = event.nativeEvent;
      apply(chatBottomAfterScroll(track.current, contentOffset.y, contentSize.height));
    },
    [apply]
  );
  const onContentSizeChange = React.useCallback(
    (_width: number, height: number) => {
      const laysOutNewer: boolean =
        track.current.newerAddedFrom !== null && !track.current.newerLaidOut;
      apply(chatBottomAfterContentSize(track.current, height));
      if (laysOutNewer) settleNewerNextFrame();
    },
    [apply, settleNewerNextFrame]
  );
  const onNewerAddedInPlace = React.useCallback(() => {
    apply(chatBottomAfterNewerAdded(track.current));
    settleNewerNextFrame();
  }, [apply, settleNewerNextFrame]);
  return { atBottom, onScroll, onScrollEnd, onContentSizeChange, onNewerAddedInPlace };
}

export interface UseChatNewestEndOptions {
  /** The newest page is loaded. */
  atNewest: boolean;
  /** The loaded messages, oldest first. */
  messages: readonly ChatMessageResponse[];
  /** Tells the list a newer page was added below the reader without scrolling to it. */
  onNewerAddedInPlace: () => void;
}

/**
 * How an inverted message list treats messages added at its newest end
 * (`maintainVisibleContentPosition`). An arrival while the reader is at the
 * newest message scrolls to it. A newer page loaded while the reader reads
 * down from the divider keeps the reader's place instead, so no unread
 * message is skipped, and the list then works out from the page's height
 * whether the reader is still at the bottom (`chatAutoscrollThreshold`,
 * `isChatNewerPageAddedInPlace`, `chatBottomAfterNewerAdded`).
 */
export function useChatNewestEnd({
  atNewest,
  messages,
  onNewerAddedInPlace,
}: UseChatNewestEndOptions) {
  const [wasAtNewest, setWasAtNewest] = React.useState<boolean>(atNewest);
  React.useEffect(() => setWasAtNewest(atNewest), [atNewest]);
  const threshold: number | undefined = chatAutoscrollThreshold({ atNewest, wasAtNewest });

  const newestMessageId: string | undefined = messages[messages.length - 1]?.id;
  const previousNewestId = React.useRef<string | undefined>(newestMessageId);
  // A layout effect runs in the same task as the commit, so it comes before any
  // scroll or size event that reports the page, and those events then count.
  React.useLayoutEffect(() => {
    const previous: string | undefined = previousNewestId.current;
    previousNewestId.current = newestMessageId;
    if (previous === newestMessageId) return;
    if (
      isChatNewerPageAddedInPlace({
        previousNewestId: previous,
        messages,
        autoscroll: threshold !== undefined,
      })
    ) {
      onNewerAddedInPlace();
    }
  }, [newestMessageId, messages, threshold, onNewerAddedInPlace]);

  return React.useMemo(
    () => ({ minIndexForVisible: 0, autoscrollToTopThreshold: threshold }),
    [threshold]
  );
}

/**
 * The reader's unread state failed to load before the conversation took its
 * read position (`isChatUnreadStateFailed`): the list shows it with a retry,
 * since it waits for that position to open at the divider and to read.
 */
export function useChatUnreadStateError(markerKnown: boolean) {
  const { isError, refetch } = useChatUnread();
  const failed: boolean = isChatUnreadStateFailed({ markerKnown, isError });
  const retry = React.useCallback(() => {
    // A failed retry keeps the query in error, which keeps the notice on screen.
    refetch().catch((error: unknown) => {
      console.warn("[chat] reloading the unread state failed", messageOf(error));
    });
  }, [refetch]);
  return { failed, retry };
}

export interface ChatUnreadPaging {
  pageCount: number;
  hasPreviousPage: boolean;
  isFetchingPreviousPage: boolean;
  isFetchPreviousPageError: boolean;
  fetchPreviousPage: () => Promise<unknown>;
}

export interface UseChatUnreadOpenOptions {
  /** The first page, the read position and the reader are known. */
  ready: boolean;
  marker: ChatUnreadMarker;
  items: readonly ChatListItem[];
  /** The loaded messages, oldest first. */
  messages: readonly ChatMessageResponse[];
  atNewest: boolean;
  atBottom: boolean;
  /** A message named on opening is shown instead. */
  jumping: boolean;
  /** The read message lies before what the list can show; see `ChatUnreadOpenInput`. */
  readBeforeList?: boolean;
  paging: ChatUnreadPaging;
  /** Replaces the list's pages by the page around a message; rejects when that fails. */
  loadAround: (messageId: string) => Promise<void>;
  /**
   * Finds a message to load around from the read time alone, when the read
   * message is gone; null when none was found. It stops reading pages when
   * `signal` fires. Lists without it look through older pages instead. See
   * `useChatLocateByReadTime`.
   */
  locateByTime?: ChatLocateByReadTime;
  scrollToRow: (index: number, position: number, animated: boolean) => void;
  /** Tells the reader the messages before the newest could not be loaded. */
  onLoadFailed: () => void;
}

export interface ChatUnreadOpen {
  phase: ChatUnreadOpenPhase;
  /** Where the list opens was decided. */
  opened: boolean;
  /** Ends the hold of a failed load, when the reader asks to mark the conversation read. */
  release: () => void;
}

function messageOf(error: unknown): unknown {
  return error instanceof Error ? error.message : error;
}

/** Finds a message to load around from the read time; see `findChatMessageNearReadTime`. */
export type ChatLocateByReadTime = (
  lastReadAt: string,
  signal: AbortSignal
) => Promise<string | null>;

/**
 * Finds, for a channel's own list, a message to load around from the read
 * time (`findChatMessageNearReadTime`). A thread has none: its pages take no
 * day filter.
 */
export function useChatLocateByReadTime(
  channelId: string,
  threadId: string | null
): ChatLocateByReadTime | undefined {
  const locate = React.useCallback(
    (lastReadAt: string, signal: AbortSignal) =>
      findChatMessageNearReadTime(
        lastReadAt,
        (day, cursor, pageSignal) => listChannelDayMessages(channelId, day, cursor, pageSignal),
        signal
      ),
    [channelId]
  );
  return threadId ? undefined : locate;
}

/**
 * Opens a conversation with unread messages at the "New messages" divider,
 * near the top of the screen, as the web does: loaded already, after older
 * pages, or around the read message. Without unread messages, or when a
 * message to jump to was named, the list stays at the bottom. The phase it
 * returns tells `isChatNewestSeen` whether the bottom on screen is the
 * reader's own place yet.
 */
export function useChatUnreadOpen({
  ready,
  marker,
  items,
  messages,
  atNewest,
  atBottom,
  jumping,
  readBeforeList = false,
  paging,
  loadAround,
  locateByTime,
  scrollToRow,
  onLoadFailed,
}: UseChatUnreadOpenOptions): ChatUnreadOpen {
  const [phase, setPhase] = React.useState<ChatUnreadOpenPhase>("deciding");
  const dividerIndex: number = items.findIndex((item) => item.kind === "unread");
  const { pageCount, hasPreviousPage, isFetchingPreviousPage, isFetchPreviousPageError } = paging;
  const { fetchPreviousPage } = paging;

  // The divider, or the oldest loaded row when the divider was not found, goes to the top of the screen.
  const placeAt = React.useCallback(
    (index: number) => {
      if (index < 0) {
        setPhase("settled");
        return;
      }
      scrollToRow(index, UNREAD_DIVIDER_VIEW_POSITION, false);
      setPhase("placing");
    },
    [scrollToRow]
  );

  const fail = React.useCallback(() => {
    onLoadFailed();
    setPhase("failed");
  }, [onLoadFailed]);

  // The read-time lookup pages through a whole day; it stops when the list closes or its lookup changes.
  const lookup = React.useRef<AbortController | null>(null);
  React.useEffect(() => {
    const controller: AbortController = new AbortController();
    lookup.current = controller;
    return () => controller.abort();
  }, [locateByTime]);

  // A message of the read time stands in for the read message; without one, older pages lead on.
  const loadAroundReadTime = React.useCallback(
    (lastReadAt: string) => {
      const signal: AbortSignal | undefined = lookup.current?.signal;
      if (!locateByTime || !signal) {
        setPhase("seeking");
        return;
      }
      setPhase("loadingAround");
      locateByTime(lastReadAt, signal)
        .then(async (anchorId: string | null) => {
          if (signal.aborted) return;
          if (!anchorId) {
            setPhase("seeking");
            return;
          }
          await loadAround(anchorId);
          if (signal.aborted) return;
          setPhase("aroundLoaded");
        })
        .catch((error: unknown) => {
          // reason: cancelled because the list closed; nobody waits for the result, so it is not an error to show.
          if (signal.aborted) return;
          console.warn(
            "[chat] finding the unread messages by the read time failed",
            messageOf(error)
          );
          fail();
        });
    },
    [locateByTime, loadAround, fail]
  );

  // Decided once, when everything it depends on is in.
  React.useEffect(() => {
    if (phase !== "deciding" || !ready) return;
    const step: ChatUnreadOpenStep = chatUnreadOpenStep({
      marker,
      dividerLoaded: dividerIndex >= 0,
      messages,
      atNewest,
      hasPreviousPage,
      jumping,
      atBottom,
      readBeforeList,
      canLocateByTime: !!locateByTime,
    });
    switch (step.kind) {
      case "stay":
      case "seekOlder":
        setPhase(chatUnreadPhaseOf(step));
        return;
      case "divider":
        placeAt(dividerIndex);
        return;
      case "locateByTime":
        loadAroundReadTime(step.lastReadAt);
        return;
      case "loadAround":
        setPhase(chatUnreadPhaseOf(step));
        loadAround(step.messageId)
          .then(() => setPhase("aroundLoaded"))
          .catch((error: unknown) => {
            if (isApiError(error, CHAT_ERROR_CODES.messageNotFound)) {
              // The read message is gone: its time still leads to the messages after it.
              const next: ChatUnreadOpenStep = chatUnreadReadMessageGoneStep({
                lastReadAt: marker.lastReadAt,
                canLocateByTime: !!locateByTime,
              });
              if (next.kind === "locateByTime") loadAroundReadTime(next.lastReadAt);
              else setPhase("seeking");
              return;
            }
            console.warn("[chat] loading the unread messages failed", messageOf(error));
            fail();
          });
        return;
    }
  }, [
    phase,
    ready,
    marker,
    dividerIndex,
    messages,
    atNewest,
    hasPreviousPage,
    jumping,
    atBottom,
    readBeforeList,
    loadAround,
    locateByTime,
    loadAroundReadTime,
    placeAt,
    fail,
  ]);

  // Older pages, one at a time, until the divider is in.
  React.useEffect(() => {
    if (phase !== "seeking") return;
    const step: ChatUnreadSeekStep = chatUnreadSeekStep({
      dividerLoaded: dividerIndex >= 0,
      pageCount,
      hasPreviousPage,
      isFetchingPreviousPage,
      isFetchPreviousPageError,
    });
    switch (step) {
      case "found":
        placeAt(dividerIndex);
        return;
      case "failed":
        console.warn("[chat] loading older messages to reach the unread ones failed");
        fail();
        return;
      case "pageLimit":
        // Not found yet: the oldest loaded messages are unread too, so the reader starts there.
        placeAt(chatUnreadOpenIndex({ dividerIndex, rowCount: items.length }));
        return;
      case "atStart":
        // Nothing loaded is unread: the list stays at the bottom, as it does with nothing unread.
        setPhase(chatUnreadPhaseOf({ kind: "stay" }));
        return;
      case "wait":
        return;
      case "fetch":
        // A failed page sets the query's error flag, which the next run reads.
        fetchPreviousPage().catch((error: unknown) => {
          console.warn("[chat] loading older messages failed", messageOf(error));
        });
        return;
    }
  }, [
    phase,
    dividerIndex,
    pageCount,
    hasPreviousPage,
    isFetchingPreviousPage,
    isFetchPreviousPageError,
    fetchPreviousPage,
    items.length,
    placeAt,
    fail,
  ]);

  // The page around the read message is in: the divider follows it, unless the reader wrote the rest.
  React.useEffect(() => {
    if (phase !== "aroundLoaded") return;
    if (dividerIndex >= 0) placeAt(dividerIndex);
    else setPhase("settled");
  }, [phase, dividerIndex, placeAt]);

  React.useEffect(() => {
    if (phase !== "placing") return;
    const timer: ReturnType<typeof setTimeout> = setTimeout(
      () => setPhase("settled"),
      DIVIDER_SETTLE_MS
    );
    return () => clearTimeout(timer);
  }, [phase]);

  // After a failed load, the reader's own way back to the bottom counts.
  React.useEffect(() => {
    if (phase === "failed" && !atBottom) setPhase("settled");
  }, [phase, atBottom]);

  const release = React.useCallback(() => {
    setPhase((current: ChatUnreadOpenPhase) => (current === "failed" ? "settled" : current));
  }, []);

  return { phase, opened: phase !== "deciding", release };
}

export interface UseChatUnreadBarOptions {
  channelId: string;
  threadId: string | null;
  marker: ChatUnreadMarker;
  /** The newest message is on screen and the list holds the newest page. */
  newestInView: boolean;
  atNewest: boolean;
  newestMessageId: string | undefined;
  /** Brings the newest messages back when they are not loaded. */
  jumpToLatest: () => void;
  /** Lets the list read the newest messages again after a failed load held it. */
  releaseReadHold: () => void;
}

/**
 * The bar above a conversation opened with unread messages. It hides once
 * the newest message has been in view for a moment, when dismissed, or when
 * Mark as read moves the read position to the newest message; the divider
 * keeps marking where the unread messages began.
 */
export function useChatUnreadBar({
  channelId,
  threadId,
  marker,
  newestInView,
  atNewest,
  newestMessageId,
  jumpToLatest,
  releaseReadHold,
}: UseChatUnreadBarOptions) {
  const { mutate } = useMarkChatRead();
  const [dismissed, setDismissed] = React.useState<boolean>(false);
  const visible: boolean = marker.count > 0 && !dismissed;

  React.useEffect(() => {
    if (!visible || !newestInView) return;
    const timer: ReturnType<typeof setTimeout> = setTimeout(
      () => setDismissed(true),
      UNREAD_BAR_HIDE_DELAY_MS
    );
    return () => clearTimeout(timer);
  }, [visible, newestInView]);

  const dismiss = React.useCallback(() => setDismissed(true), []);

  const markAllRead = React.useCallback(() => {
    setDismissed(true);
    releaseReadHold();
    if (!atNewest || !newestMessageId) {
      // The newest messages are read once they are in view.
      jumpToLatest();
      return;
    }
    // A failed write is logged by the hook and toasted by the app-wide notices.
    mutate({ channelId, threadId, lastReadMessageId: newestMessageId });
  }, [atNewest, newestMessageId, jumpToLatest, releaseReadHold, mutate, channelId, threadId]);

  return { visible, dismiss, markAllRead };
}
