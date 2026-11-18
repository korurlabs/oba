import { describe, expect, it } from "bun:test";

import {
  findChatFirstUnreadSinceJoin,
  type ChatUnreadMarker,
} from "@/components/chat/chatListModel";
import { shouldMarkChatRead, type ChatMarkReadInput } from "@/components/chat/chatScreenModel";
import {
  CHAT_AT_BOTTOM_PX,
  CHAT_LOCATE_READ_MAX_PAGES,
  CHAT_SEEK_UNREAD_MAX_PAGES,
  chatAutoscrollThreshold,
  chatUnreadOpenIndex,
  chatUnreadOpenStep,
  chatUnreadPhaseOf,
  chatUnreadReadMessageGoneStep,
  chatUnreadSeekStep,
  findChatMessageNearReadTime,
  heldChatUnreadMarker,
  isChatNewerPageAddedInPlace,
  isChatNewestSeen,
  isChatUnreadStateFailed,
  type ChatHeldUnreadMarker,
  type ChatReadGateInput,
  type ChatUnreadOpenInput,
  type ChatUnreadOpenStep,
  type ChatUnreadSeekInput,
  type ChatUnreadSeekStep,
} from "@/components/chat/chatUnreadModel";
import {
  CHAT_PAGE_SIZE,
  CHAT_UNREAD_CAP,
  type ChatConversationUnreadResponse,
  type ChatMessageCursor,
  type ChatMessagePage,
  type ChatMessageResponse,
} from "@/lib/schemas/chat";

import { ME, chatMessage } from "./chatFixtures";

const marker = (overrides: Partial<ChatUnreadMarker>): ChatUnreadMarker => ({
  lastReadMessageId: "read",
  lastReadAt: "2026-11-04T09:00:00.000Z",
  count: 3,
  ...overrides,
});

describe("chatUnreadOpenStep", () => {
  const loaded = [
    chatMessage({ id: "m1", createdAt: "2026-11-05T10:00:00.000Z" }),
    chatMessage({ id: "m2", createdAt: "2026-11-05T10:01:00.000Z" }),
  ];
  const input: ChatUnreadOpenInput = {
    marker: marker({}),
    dividerLoaded: false,
    messages: loaded,
    atNewest: true,
    hasPreviousPage: true,
    jumping: false,
    atBottom: true,
    readBeforeList: false,
    canLocateByTime: true,
  };

  it("scrolls to a loaded divider", () => {
    expect(chatUnreadOpenStep({ ...input, dividerLoaded: true })).toEqual({ kind: "divider" });
  });

  it("stays with nothing unread, for a jump, or once the reader scrolled", () => {
    expect(chatUnreadOpenStep({ ...input, marker: marker({ count: 0 }) })).toEqual({
      kind: "stay",
    });
    expect(chatUnreadOpenStep({ ...input, jumping: true })).toEqual({ kind: "stay" });
    expect(chatUnreadOpenStep({ ...input, atBottom: false })).toEqual({ kind: "stay" });
  });

  it("loads around the read message when it is older than the loaded ones", () => {
    expect(chatUnreadOpenStep(input)).toEqual({ kind: "loadAround", messageId: "read" });
  });

  it("loads older pages when the read message lies before the list's day", () => {
    expect(chatUnreadOpenStep({ ...input, readBeforeList: true })).toEqual({ kind: "seekOlder" });
  });

  it("loads older pages to count back from the newest when the read message is unknown", () => {
    const sinceJoin = marker({ lastReadMessageId: null, lastReadAt: null });
    expect(chatUnreadOpenStep({ ...input, marker: sinceJoin })).toEqual({ kind: "seekOlder" });
  });

  it("stays when everything is loaded and nothing after the read message is someone else's", () => {
    expect(chatUnreadOpenStep({ ...input, hasPreviousPage: false })).toEqual({ kind: "stay" });
    const readLoaded = marker({ lastReadMessageId: "m2", lastReadAt: "2026-11-05T10:01:00.000Z" });
    expect(chatUnreadOpenStep({ ...input, marker: readLoaded })).toEqual({ kind: "stay" });
  });
});

describe("chatUnreadSeekStep", () => {
  const input: ChatUnreadSeekInput = {
    dividerLoaded: false,
    pageCount: 1,
    hasPreviousPage: true,
    isFetchingPreviousPage: false,
    isFetchPreviousPageError: false,
  };

  it("fetches one older page at a time until the divider is in", () => {
    expect(chatUnreadSeekStep(input)).toBe("fetch");
    expect(chatUnreadSeekStep({ ...input, isFetchingPreviousPage: true })).toBe("wait");
    expect(chatUnreadSeekStep({ ...input, dividerLoaded: true })).toBe("found");
  });

  it("reports a failed page", () => {
    expect(chatUnreadSeekStep({ ...input, isFetchPreviousPageError: true })).toBe("failed");
  });

  it("stops at the start of the conversation or at the page limit, and tells them apart", () => {
    expect(chatUnreadSeekStep({ ...input, hasPreviousPage: false })).toBe("atStart");
    expect(chatUnreadSeekStep({ ...input, pageCount: CHAT_SEEK_UNREAD_MAX_PAGES })).toBe(
      "pageLimit"
    );
    expect(
      chatUnreadSeekStep({
        ...input,
        hasPreviousPage: false,
        pageCount: CHAT_SEEK_UNREAD_MAX_PAGES,
      })
    ).toBe("atStart");
  });
});

describe("read marking", () => {
  const seen: ChatReadGateInput = {
    phase: "settled",
    live: true,
    loaded: true,
    atNewest: true,
    atBottom: true,
  };
  const request = (newestSeen: boolean, overrides: Partial<ChatMarkReadInput> = {}): boolean =>
    shouldMarkChatRead({
      newestSeen,
      visible: true,
      pending: true,
      newestMessageId: "m9",
      lastSentMessageId: null,
      ...overrides,
    });

  it("does not read a conversation only because it opened at the divider", () => {
    // The scroll to the divider has not landed: the bottom it opened at is not the reader's.
    expect(request(isChatNewestSeen({ ...seen, phase: "placing" }))).toBe(false);
    // It landed away from the bottom.
    expect(request(isChatNewestSeen({ ...seen, atBottom: false }))).toBe(false);
  });

  it("reads it once the reader is at the bottom, also when everything unread fits on screen", () => {
    expect(request(isChatNewestSeen(seen))).toBe(true);
  });

  it("reads nothing while the reader is scrolled up or the newest page is not loaded", () => {
    expect(request(isChatNewestSeen({ ...seen, atBottom: false }))).toBe(false);
    expect(request(isChatNewestSeen({ ...seen, atNewest: false }))).toBe(false);
  });

  it("reads a message that arrives while the reader is at the bottom", () => {
    expect(
      request(isChatNewestSeen(seen), { newestMessageId: "m10", lastSentMessageId: "m9" })
    ).toBe(true);
    // Not the same message twice while the server still counts something.
    expect(request(isChatNewestSeen(seen), { lastSentMessageId: "m9" })).toBe(false);
  });

  it("reads nothing while the divider loads, or after loading it failed", () => {
    for (const phase of [
      "deciding",
      "seeking",
      "loadingAround",
      "aroundLoaded",
      "failed",
    ] as const) {
      expect(request(isChatNewestSeen({ ...seen, phase }))).toBe(false);
    }
  });

  it("reads nothing in a past day or a filtered list", () => {
    expect(request(isChatNewestSeen({ ...seen, live: false }))).toBe(false);
  });
});

describe("heldChatUnreadMarker", () => {
  const entry: ChatConversationUnreadResponse = {
    id: "c1",
    channelId: "c1",
    unread: 4,
    mentionCount: 0,
    lastReadMessageId: "read",
    lastReadAt: "2026-11-04T09:00:00.000Z",
  };
  const fromEntry: ChatUnreadMarker = {
    lastReadMessageId: "read",
    lastReadAt: "2026-11-04T09:00:00.000Z",
    count: 4,
  };

  it("holds no marker while the unread state is not loaded, a failed load included", () => {
    expect(heldChatUnreadMarker(null, "c1", { loaded: false, entry: undefined })).toBe(null);
  });

  it("takes the real marker once a load after a failure succeeds", () => {
    const waiting: ChatHeldUnreadMarker | null = heldChatUnreadMarker(null, "c1", {
      loaded: false,
      entry: undefined,
    });
    expect(heldChatUnreadMarker(waiting, "c1", { loaded: true, entry })).toEqual({
      conversationId: "c1",
      marker: fromEntry,
    });
  });

  it("keeps the marker of the opening while the read position moves", () => {
    const held: ChatHeldUnreadMarker = { conversationId: "c1", marker: fromEntry };
    const read: ChatConversationUnreadResponse = { ...entry, unread: 0, lastReadMessageId: "new" };
    expect(heldChatUnreadMarker(held, "c1", { loaded: true, entry: read })).toBe(held);
    expect(heldChatUnreadMarker(held, "c1", { loaded: false, entry: undefined })).toBe(held);
  });

  it("takes a new marker for another conversation, and none while its state is unknown", () => {
    const held: ChatHeldUnreadMarker = { conversationId: "c1", marker: fromEntry };
    expect(heldChatUnreadMarker(held, "c2", { loaded: true, entry: undefined })).toEqual({
      conversationId: "c2",
      marker: { lastReadMessageId: null, lastReadAt: null, count: 0 },
    });
    expect(
      heldChatUnreadMarker(held, "c2", { loaded: false, entry: undefined })?.conversationId
    ).toBe("c1");
  });
});

describe("isChatUnreadStateFailed", () => {
  it("tells the reader only while the list waits on a failed load", () => {
    expect(isChatUnreadStateFailed({ markerKnown: false, isError: true })).toBe(true);
    expect(isChatUnreadStateFailed({ markerKnown: false, isError: false })).toBe(false);
    // A marker from an earlier load stays good when a later refresh fails.
    expect(isChatUnreadStateFailed({ markerKnown: true, isError: true })).toBe(false);
  });

  it("reads nothing while the list waits for the unread state", () => {
    // Without a marker the open is never decided, so the list stays deciding.
    expect(
      isChatNewestSeen({
        phase: "deciding",
        live: true,
        loaded: true,
        atNewest: true,
        atBottom: true,
      })
    ).toBe(false);
  });
});

describe("chatAutoscrollThreshold", () => {
  it("scrolls to arrivals while the newest page was already loaded", () => {
    expect(chatAutoscrollThreshold({ atNewest: true, wasAtNewest: true })).toBe(CHAT_AT_BOTTOM_PX);
  });

  it("does not scroll past a newer page while reading down from the divider", () => {
    expect(chatAutoscrollThreshold({ atNewest: false, wasAtNewest: false })).toBeUndefined();
    // The page that turns out to be the newest is still read, not skipped.
    expect(chatAutoscrollThreshold({ atNewest: true, wasAtNewest: false })).toBeUndefined();
    expect(chatAutoscrollThreshold({ atNewest: false, wasAtNewest: true })).toBeUndefined();
  });
});

describe("isChatNewerPageAddedInPlace", () => {
  const messages = [
    chatMessage({ id: "m1" }),
    chatMessage({ id: "m2" }),
    chatMessage({ id: "m3" }),
  ];

  it("moves the reader off the bottom when a newer page kept the reader's place", () => {
    expect(
      isChatNewerPageAddedInPlace({ previousNewestId: "m1", messages, autoscroll: false })
    ).toBe(true);
  });

  it("leaves the bottom to an arrival the list scrolled to", () => {
    expect(
      isChatNewerPageAddedInPlace({ previousNewestId: "m2", messages, autoscroll: true })
    ).toBe(false);
  });

  it("ignores pages that replaced the list or added nothing below", () => {
    expect(
      isChatNewerPageAddedInPlace({ previousNewestId: "gone", messages, autoscroll: false })
    ).toBe(false);
    expect(
      isChatNewerPageAddedInPlace({ previousNewestId: "m3", messages, autoscroll: false })
    ).toBe(false);
    expect(
      isChatNewerPageAddedInPlace({ previousNewestId: undefined, messages, autoscroll: false })
    ).toBe(false);
  });
});

/** `total` messages by Alice, a minute apart, oldest first, ids `m0` to `m<total - 1>`. */
function minuteMessages(
  total: number,
  from: string = "2026-11-05T08:00:00.000Z"
): ChatMessageResponse[] {
  const start: number = Date.parse(from);
  return Array.from({ length: total }, (_, index: number) =>
    chatMessage({ id: `m${index}`, createdAt: new Date(start + index * 60_000).toISOString() })
  );
}

describe("opening with a capped or unplaceable unread count", () => {
  const base: Omit<ChatUnreadOpenInput, "marker" | "messages"> = {
    dividerLoaded: false,
    atNewest: true,
    hasPreviousPage: true,
    jumping: false,
    atBottom: true,
    readBeforeList: false,
    canLocateByTime: true,
  };
  const gate = (phase: ChatReadGateInput["phase"]): boolean =>
    isChatNewestSeen({ phase, live: true, loaded: true, atNewest: true, atBottom: true });

  it("seeks older pages for 100 or more unread since join, and stops at the 100th", () => {
    const capped: ChatUnreadMarker = { lastReadMessageId: null, lastReadAt: null, count: 100 };
    const newestPage: ChatMessageResponse[] = minuteMessages(CHAT_PAGE_SIZE);
    const step: ChatUnreadOpenStep = chatUnreadOpenStep({
      ...base,
      marker: capped,
      messages: newestPage,
    });
    expect(step).toEqual({ kind: "seekOlder" });
    // One page does not hold 100 messages yet.
    expect(findChatFirstUnreadSinceJoin(newestPage, capped.count, ME, true, false)).toBe(null);
    // Three pages do: the divider goes above the oldest message known to be unread.
    const threePages: ChatMessageResponse[] = minuteMessages(3 * CHAT_PAGE_SIZE);
    const hundredth: string | null = findChatFirstUnreadSinceJoin(threePages, 100, ME, true, false);
    expect(hundredth).toBe(`m${3 * CHAT_PAGE_SIZE - CHAT_UNREAD_CAP}`);
    expect(
      chatUnreadSeekStep({
        dividerLoaded: hundredth !== null,
        pageCount: 3,
        hasPreviousPage: true,
        isFetchingPreviousPage: false,
        isFetchPreviousPageError: false,
      })
    ).toBe("found");
  });

  it("finds the unread messages by the read time when the read message is gone", () => {
    const gone: ChatUnreadMarker = {
      lastReadMessageId: null,
      lastReadAt: "2026-11-05T07:00:00.000Z",
      count: 100,
    };
    const step: ChatUnreadOpenStep = chatUnreadOpenStep({
      ...base,
      marker: gone,
      messages: minuteMessages(CHAT_PAGE_SIZE),
    });
    expect(step).toEqual({ kind: "locateByTime", lastReadAt: "2026-11-05T07:00:00.000Z" });
    // A thread has no day filter: it looks through older pages.
    expect(
      chatUnreadOpenStep({
        ...base,
        canLocateByTime: false,
        marker: gone,
        messages: minuteMessages(2),
      })
    ).toEqual({ kind: "seekOlder" });
    // The around-load answered that the read message no longer exists.
    expect(
      chatUnreadReadMessageGoneStep({ lastReadAt: gone.lastReadAt, canLocateByTime: true })
    ).toEqual({ kind: "locateByTime", lastReadAt: "2026-11-05T07:00:00.000Z" });
    expect(chatUnreadReadMessageGoneStep({ lastReadAt: null, canLocateByTime: true })).toEqual({
      kind: "seekOlder",
    });
  });

  it("opens at the oldest loaded row when more than the page limit is unread", () => {
    const seek: ChatUnreadSeekInput = {
      dividerLoaded: false,
      pageCount: CHAT_SEEK_UNREAD_MAX_PAGES,
      hasPreviousPage: true,
      isFetchingPreviousPage: false,
      isFetchPreviousPageError: false,
    };
    expect(chatUnreadSeekStep(seek)).toBe("pageLimit");
    const rowCount: number = CHAT_SEEK_UNREAD_MAX_PAGES * CHAT_PAGE_SIZE + 1;
    expect(chatUnreadOpenIndex({ dividerIndex: -1, rowCount })).toBe(rowCount - 1);
    expect(chatUnreadOpenIndex({ dividerIndex: 7, rowCount })).toBe(7);
    expect(chatUnreadOpenIndex({ dividerIndex: -1, rowCount: 0 })).toBe(-1);
  });

  it("reads neither case at open: every step goes through a phase that holds reading", () => {
    const capped: ChatUnreadMarker = { lastReadMessageId: null, lastReadAt: null, count: 100 };
    const gone: ChatUnreadMarker = {
      lastReadMessageId: null,
      lastReadAt: "2026-11-05T07:00:00.000Z",
      count: 100,
    };
    for (const marker of [capped, gone]) {
      for (const canLocateByTime of [true, false]) {
        const step: ChatUnreadOpenStep = chatUnreadOpenStep({
          ...base,
          canLocateByTime,
          marker,
          messages: minuteMessages(CHAT_PAGE_SIZE),
        });
        expect(step.kind).not.toBe("stay");
        expect(gate(chatUnreadPhaseOf(step))).toBe(false);
      }
    }
    // A seek stopped by the page limit scrolls to the oldest row, which enters `placing`, not `settled`.
    expect(gate(chatUnreadPhaseOf({ kind: "divider" }))).toBe(false);
  });

  it("stays at the bottom when the start is loaded and nothing is unread after all", () => {
    // The count was taken before the unread messages were deleted: only the reader's own are left.
    const stale: ChatUnreadMarker = { lastReadMessageId: null, lastReadAt: null, count: 5 };
    const own: ChatMessageResponse[] = minuteMessages(3).map((message: ChatMessageResponse) => ({
      ...message,
      author: { userId: ME, email: "me@example.com", firstName: "Me", lastName: "Reader" },
    }));
    expect(findChatFirstUnreadSinceJoin(own, stale.count, ME, true, true)).toBe(null);
    const step: ChatUnreadSeekStep = chatUnreadSeekStep({
      dividerLoaded: false,
      pageCount: 2,
      hasPreviousPage: false,
      isFetchingPreviousPage: false,
      isFetchPreviousPageError: false,
    });
    expect(step).toBe("atStart");
    // The list then settles as one with nothing unread: the reader at the bottom reads it.
    expect(gate(chatUnreadPhaseOf({ kind: "stay" }))).toBe(true);
  });

  it("opens at the first unread message when the start is loaded and the count still holds", () => {
    // Five counted, two of them deleted since: the three left are all unread.
    const stale: ChatUnreadMarker = { lastReadMessageId: null, lastReadAt: null, count: 5 };
    const left: ChatMessageResponse[] = minuteMessages(3);
    const first: string | null = findChatFirstUnreadSinceJoin(left, stale.count, ME, true, true);
    expect(first).toBe("m0");
    expect(
      chatUnreadSeekStep({
        dividerLoaded: first !== null,
        pageCount: 2,
        hasPreviousPage: false,
        isFetchingPreviousPage: false,
        isFetchPreviousPageError: false,
      })
    ).toBe("found");
  });
});

describe("findChatMessageNearReadTime", () => {
  type Call = { day: string; cursor: ChatMessageCursor };
  const open: AbortSignal = new AbortController().signal;

  /** Serves a day's messages newest page first, as the server pages with `before`. */
  function dayServer(day: ChatMessageResponse[], pageSize: number = 3) {
    const calls: Call[] = [];
    const signals: AbortSignal[] = [];
    const fetch = async (
      date: string,
      cursor: ChatMessageCursor,
      signal: AbortSignal
    ): Promise<ChatMessagePage> => {
      calls.push({ day: date, cursor });
      signals.push(signal);
      const end: number = cursor.before
        ? day.findIndex((message: ChatMessageResponse) => message.id === cursor.before)
        : day.length;
      const start: number = Math.max(0, end - pageSize);
      return { items: day.slice(start, end), hasOlder: start > 0, hasNewer: !!cursor.before };
    };
    return { calls, signals, fetch };
  }

  it("asks for the read time's business day and returns the message at or before it", async () => {
    // 21:10 UTC is already the next day in Istanbul (UTC+3).
    const day: ChatMessageResponse[] = minuteMessages(8, "2026-11-04T21:10:00.000Z");
    const server = dayServer(day);
    await expect(
      findChatMessageNearReadTime("2026-11-04T21:11:30.000Z", server.fetch, open)
    ).resolves.toBe("m1");
    expect(server.calls[0]).toEqual({ day: "2026-11-05", cursor: {} });
    // m1 was two pages back from the newest.
    expect(server.calls.map((call: Call) => call.cursor)).toEqual([
      {},
      { before: "m5" },
      { before: "m2" },
    ]);
  });

  it("returns the day's first message when the read time comes before the whole day", async () => {
    const server = dayServer(minuteMessages(5));
    await expect(
      findChatMessageNearReadTime("2026-11-05T06:00:00.000Z", server.fetch, open)
    ).resolves.toBe("m0");
  });

  it("finds nothing on an empty day or past the page limit", async () => {
    await expect(
      findChatMessageNearReadTime("2026-11-05T06:00:00.000Z", dayServer([]).fetch, open)
    ).resolves.toBe(null);
    const busy = dayServer(minuteMessages(CHAT_LOCATE_READ_MAX_PAGES * 3 + 3), 3);
    await expect(
      findChatMessageNearReadTime("2026-11-05T06:00:00.000Z", busy.fetch, open)
    ).resolves.toBe(null);
    expect(busy.calls).toHaveLength(CHAT_LOCATE_READ_MAX_PAGES);
  });

  it("rejects when a page fails, so the list reports it", async () => {
    const failing = async (): Promise<ChatMessagePage> => {
      throw new Error("offline");
    };
    await expect(
      findChatMessageNearReadTime("2026-11-05T06:00:00.000Z", failing, open)
    ).rejects.toThrow("offline");
  });

  it("passes the signal to every page and reads no further page once it fires", async () => {
    const server = dayServer(minuteMessages(9));
    const controller: AbortController = new AbortController();
    const stopping = async (
      date: string,
      cursor: ChatMessageCursor,
      signal: AbortSignal
    ): Promise<ChatMessagePage> => {
      const page: ChatMessagePage = await server.fetch(date, cursor, signal);
      // The list closes while the first page is in flight.
      controller.abort();
      return page;
    };
    await expect(
      findChatMessageNearReadTime("2026-11-05T06:00:00.000Z", stopping, controller.signal)
    ).resolves.toBe(null);
    expect(server.calls).toHaveLength(1);
    expect(server.signals[0]).toBe(controller.signal);
  });

  it("reads nothing when the signal fired before it started", async () => {
    const server = dayServer(minuteMessages(3));
    const controller: AbortController = new AbortController();
    controller.abort();
    await expect(
      findChatMessageNearReadTime("2026-11-05T06:00:00.000Z", server.fetch, controller.signal)
    ).resolves.toBe(null);
    expect(server.calls).toHaveLength(0);
  });
});
