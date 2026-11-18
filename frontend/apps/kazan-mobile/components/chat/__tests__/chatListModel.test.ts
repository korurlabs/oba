import { describe, expect, it } from "bun:test";

import {
  buildChatListItems,
  findChatFirstUnread,
  findChatFirstUnreadSinceJoin,
  isChatGroupedWith,
  isChatUnreadSinceJoin,
  type ChatUnreadMarker,
} from "@/components/chat/chatListModel";
import { CHAT_UNREAD_CAP } from "@/lib/schemas/chat";

import { ME, chatMessage, chatOutboxRow } from "./chatFixtures";

const dayOf = (iso: string): string => iso.slice(0, 10);

describe("isChatGroupedWith", () => {
  const first = chatMessage({ id: "m1", createdAt: "2026-11-05T10:00:00.000Z" });

  it("groups the same author within the window", () => {
    const next = chatMessage({ id: "m2", createdAt: "2026-11-05T10:06:00.000Z" });
    expect(isChatGroupedWith(first, next, dayOf)).toBe(true);
  });

  it("starts a new header after the window, for another author, a reply or a card", () => {
    expect(
      isChatGroupedWith(
        first,
        chatMessage({ id: "m2", createdAt: "2026-11-05T10:08:00.000Z" }),
        dayOf
      )
    ).toBe(false);
    expect(
      isChatGroupedWith(
        first,
        chatMessage({ id: "m2", author: { userId: ME, email: "me@example.com" } }),
        dayOf
      )
    ).toBe(false);
    expect(
      isChatGroupedWith(
        first,
        chatMessage({
          id: "m2",
          replyTo: {
            messageId: "m1",
            deleted: false,
            authorId: null,
            snippet: "",
            attachments: [],
          },
        }),
        dayOf
      )
    ).toBe(false);
    expect(isChatGroupedWith(first, chatMessage({ id: "m2", dailyUpdateId: "du" }), dayOf)).toBe(
      false
    );
  });

  it("starts a new header on a new day", () => {
    const late = chatMessage({ id: "m1", createdAt: "2026-11-05T23:58:00.000Z" });
    const early = chatMessage({ id: "m2", createdAt: "2026-11-06T00:01:00.000Z" });
    expect(isChatGroupedWith(late, early, dayOf)).toBe(false);
  });
});

describe("buildChatListItems", () => {
  const messages = [
    chatMessage({ id: "m1", createdAt: "2026-11-04T10:00:00.000Z" }),
    chatMessage({ id: "m2", createdAt: "2026-11-05T10:00:00.000Z" }),
    chatMessage({ id: "m3", createdAt: "2026-11-05T10:01:00.000Z" }),
  ];

  it("returns newest first with day separators, the unread divider and grouping", () => {
    const items = buildChatListItems(messages, [chatOutboxRow()], {
      firstUnreadId: "m3",
      dayOf,
    });
    expect(items.map((item) => `${item.kind}:${item.key}`)).toEqual([
      "outbox:nonce-1",
      "message:m3",
      "unread:unread",
      "message:m2",
      "day:day-2026-11-05",
      "message:m1",
      "day:day-2026-11-04",
    ]);
    const m3 = items[1];
    // The divider between them breaks the group.
    expect(m3?.kind === "message" && m3.grouped).toBe(false);
  });

  it("groups consecutive messages when no divider stands between them", () => {
    const items = buildChatListItems(messages, [], { firstUnreadId: null, dayOf });
    expect(items.some((item) => item.kind === "unread")).toBe(false);
    const m3 = items[0];
    expect(m3?.kind === "message" && m3.grouped).toBe(true);
  });

  it("puts the divider above the first unread message, after its day separator", () => {
    const items = buildChatListItems(messages, [], { firstUnreadId: "m1", dayOf });
    expect(items.map((item) => item.kind).slice(-3)).toEqual(["message", "unread", "day"]);
  });
});

describe("first unread message", () => {
  const mine = { userId: ME, email: "me@example.com" };
  const messages = [
    chatMessage({ id: "m1", createdAt: "2026-11-05T10:00:00.000Z" }),
    chatMessage({ id: "m2", createdAt: "2026-11-05T10:01:00.000Z", author: mine }),
    chatMessage({ id: "m3", createdAt: "2026-11-05T10:02:00.000Z" }),
    chatMessage({ id: "m4", createdAt: "2026-11-05T10:03:00.000Z", deleted: true }),
    chatMessage({ id: "m5", createdAt: "2026-11-05T10:04:00.000Z" }),
  ];
  const marker = (overrides: Partial<ChatUnreadMarker>): ChatUnreadMarker => ({
    lastReadMessageId: null,
    lastReadAt: null,
    count: 2,
    ...overrides,
  });

  it("follows the read message, skipping the reader's own", () => {
    expect(findChatFirstUnread(messages, marker({ lastReadMessageId: "m1" }), ME, false)).toBe(
      "m3"
    );
    expect(findChatFirstUnread(messages, marker({ lastReadMessageId: "m5" }), ME, true)).toBe(null);
  });

  it("falls back to the read time when the read message is not loaded", () => {
    const byTime = marker({ lastReadMessageId: "gone", lastReadAt: "2026-11-05T10:01:30.000Z" });
    expect(findChatFirstUnread(messages, byTime, ME, false)).toBe("m3");
    // Everything loaded is newer and older pages exist: the divider lies before them.
    const older = marker({ lastReadMessageId: "gone", lastReadAt: "2026-11-04T10:00:00.000Z" });
    expect(findChatFirstUnread(messages, older, ME, false)).toBe(null);
    expect(findChatFirstUnread(messages, older, ME, true)).toBe("m1");
  });

  it("counts back from the newest when the read message is unknown", () => {
    expect(isChatUnreadSinceJoin(marker({}))).toBe(true);
    expect(isChatUnreadSinceJoin(marker({ lastReadAt: "2026-11-05T10:00:00.000Z" }))).toBe(false);
    // The deleted message and the reader's own do not count.
    expect(findChatFirstUnreadSinceJoin(messages, 2, ME, true, false)).toBe("m3");
    expect(findChatFirstUnreadSinceJoin(messages, 3, ME, true, false)).toBe("m1");
    expect(findChatFirstUnreadSinceJoin(messages, 4, ME, true, false)).toBe(null);
    expect(findChatFirstUnreadSinceJoin(messages, 2, ME, false, false)).toBe(null);
    // A capped count with fewer messages loaded: older pages are needed first.
    expect(findChatFirstUnreadSinceJoin(messages, CHAT_UNREAD_CAP, ME, true, false)).toBe(null);
  });

  it("puts the divider above the oldest counted message when the count outlasts the conversation", () => {
    // Messages counted as unread were deleted since: all that are left are unread.
    expect(findChatFirstUnreadSinceJoin(messages, 4, ME, true, true)).toBe("m1");
    expect(findChatFirstUnreadSinceJoin(messages, CHAT_UNREAD_CAP, ME, true, true)).toBe("m1");
    // Only the reader's own and deleted messages are left: nothing is unread.
    const ownOnly = messages.filter((message) => message.deleted || message.author?.userId === ME);
    expect(findChatFirstUnreadSinceJoin(ownOnly, 4, ME, true, true)).toBe(null);
  });
});
