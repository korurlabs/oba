import { describe, expect, it } from "bun:test";

import {
  chatChannelScreenFollow,
  chatChannelUnread,
  chatThreadUnread,
  chatConnectionBanner,
  chatDayKind,
  chatHomeSections,
  chatPreviewLine,
  chatPurposeScreenFollow,
  chatSwitcherChannels,
  chatTypingLine,
  CHAT_PREVIEW_MAX,
  shouldMarkChatRead,
  type ChatMarkReadInput,
} from "@/components/chat/chatScreenModel";
import { ChatChannelPurpose, type ChatMessageResponse } from "@/lib/schemas/chat";

import { ALICE, ME, chatChannel, chatMessage } from "./chatFixtures";

// A zone west of UTC, so a day read in the wrong zone shows up as a shift.
process.env.TZ = "America/New_York";

describe("chatConnectionBanner", () => {
  it("names a lost network first, whatever the session says", () => {
    expect(
      chatConnectionBanner({ status: "connected", errorCode: null, networkOnline: false }, false)
    ).toBe("networkOffline");
  });

  it("tells a refused session apart from one that gave up", () => {
    expect(
      chatConnectionBanner(
        { status: "offline", errorCode: "RTM_SEC_002", networkOnline: true },
        false
      )
    ).toBe("noAccess");
    expect(
      chatConnectionBanner({ status: "offline", errorCode: null, networkOnline: true }, false)
    ).toBe("offline");
  });

  it("stays quiet while live and during a short reconnect", () => {
    expect(
      chatConnectionBanner({ status: "connected", errorCode: null, networkOnline: true }, true)
    ).toBeNull();
    expect(
      chatConnectionBanner({ status: "reconnecting", errorCode: null, networkOnline: true }, false)
    ).toBeNull();
    expect(
      chatConnectionBanner({ status: "reconnecting", errorCode: null, networkOnline: true }, true)
    ).toBe("reconnecting");
  });
});

describe("chatHomeSections", () => {
  it("puts daily updates before pull requests and keeps the reader's order for chats", () => {
    const pulls = chatChannel({ id: "pr", purpose: "PULL_REQUESTS" });
    const daily = chatChannel({ id: "du", purpose: "DAILY_UPDATES" });
    const b = chatChannel({ id: "b" });
    const a = chatChannel({ id: "a" });
    const sections = chatHomeSections([b, pulls, a, daily]);
    expect(sections.purpose.map((channel) => channel.id)).toEqual(["du", "pr"]);
    expect(sections.chats.map((channel) => channel.id)).toEqual(["b", "a"]);
  });

  it("leaves out a purpose channel that does not exist", () => {
    expect(chatHomeSections([chatChannel()]).purpose).toEqual([]);
  });
});

describe("chatChannelUnread", () => {
  const unread = {
    channels: [
      {
        id: "c1",
        channelId: "c1",
        unread: 2,
        mentionCount: 0,
        lastReadMessageId: "m1",
        lastReadAt: null,
      },
    ],
    threads: [],
    total: 2,
    mentionTotal: 0,
  };

  it("finds the channel's own entry", () => {
    expect(chatChannelUnread(unread, "c1")?.unread).toBe(2);
  });

  it("answers undefined without data or channel", () => {
    expect(chatChannelUnread(undefined, "c1")).toBeUndefined();
    expect(chatChannelUnread(unread, null)).toBeUndefined();
    expect(chatChannelUnread(unread, "c2")).toBeUndefined();
  });
});

describe("chatThreadUnread", () => {
  it("finds the thread's entry by thread ID, not a channel's", () => {
    const entry = {
      id: "t1",
      channelId: "c1",
      unread: 3,
      mentionCount: 1,
      lastReadMessageId: null,
      lastReadAt: null,
    };
    const unread = {
      channels: [{ ...entry, id: "c1" }],
      threads: [entry],
      total: 3,
      mentionTotal: 1,
    };
    expect(chatThreadUnread(unread, "t1")?.unread).toBe(3);
    expect(chatThreadUnread(unread, "c1")).toBeUndefined();
    expect(chatThreadUnread(unread, null)).toBeUndefined();
  });
});

describe("chatPreviewLine", () => {
  const labels = { you: "You", unknownMember: "Unknown member" };
  const authorName = (message: ChatMessageResponse): string => message.author?.firstName ?? "";

  it("prefixes the author, or You for the reader's own message", () => {
    expect(chatPreviewLine(chatMessage(), ME, labels, authorName)).toBe("Alice: hello");
    const mine = chatMessage({ author: { userId: ME, email: "me@example.com", firstName: "Me" } });
    expect(chatPreviewLine(mine, ME, labels, authorName)).toBe("You: hello");
  });

  it("shows a message without author as its text and no message as null", () => {
    expect(chatPreviewLine(chatMessage({ author: null }), ME, labels, authorName)).toBe("hello");
    expect(chatPreviewLine(null, ME, labels, authorName)).toBeNull();
    expect(chatPreviewLine(undefined, ME, labels, authorName)).toBeNull();
  });

  it("cuts a long text with an ellipsis", () => {
    const line: string | null = chatPreviewLine(
      chatMessage({ content: "x".repeat(CHAT_PREVIEW_MAX + 20) }),
      ME,
      labels,
      authorName
    );
    expect(line).toBe(`Alice: ${"x".repeat(CHAT_PREVIEW_MAX)}…`);
  });

  it("names a mentioned user instead of the token", () => {
    const message = chatMessage({ content: `hi <@${ALICE}>` });
    expect(chatPreviewLine(message, ME, labels, authorName)).toBe("Alice: hi @Alice Akın");
  });
});

describe("chatTypingLine", () => {
  it("picks the phrase by how many people type", () => {
    expect(chatTypingLine([])).toBeNull();
    expect(chatTypingLine(["Alice"])).toEqual({ key: "chatTypingOne", params: { name: "Alice" } });
    expect(chatTypingLine(["Alice", "Bob"])).toEqual({
      key: "chatTypingTwo",
      params: { first: "Alice", second: "Bob" },
    });
    expect(chatTypingLine(["Alice", "Bob", "Cem"])).toEqual({ key: "chatTypingMany", params: {} });
  });
});

describe("shouldMarkChatRead", () => {
  const ready: ChatMarkReadInput = {
    newestSeen: true,
    visible: true,
    pending: true,
    newestMessageId: "m9",
    lastSentMessageId: null,
  };

  it("moves the position when the reader sees the newest unread message", () => {
    expect(shouldMarkChatRead(ready)).toBe(true);
  });

  it("waits while any condition is missing", () => {
    expect(shouldMarkChatRead({ ...ready, newestSeen: false })).toBe(false);
    expect(shouldMarkChatRead({ ...ready, visible: false })).toBe(false);
    expect(shouldMarkChatRead({ ...ready, pending: false })).toBe(false);
    expect(shouldMarkChatRead({ ...ready, newestMessageId: undefined })).toBe(false);
  });

  it("does not send the same position twice", () => {
    expect(shouldMarkChatRead({ ...ready, lastSentMessageId: "m9" })).toBe(false);
    expect(shouldMarkChatRead({ ...ready, lastSentMessageId: "m8" })).toBe(true);
  });
});

describe("chatDayKind", () => {
  const now = new Date("2026-11-06T15:00:00Z");

  it("reads the day in the device zone", () => {
    expect(chatDayKind("2026-11-06T12:00:00Z", now)).toBe("today");
    // 03:00 UTC on 6 Nov is still 5 Nov in New York.
    expect(chatDayKind("2026-11-06T03:00:00Z", now)).toBe("yesterday");
    expect(chatDayKind("2026-11-04T12:00:00Z", now)).toBe("date");
  });
});

describe("chatSwitcherChannels", () => {
  const ordered = [
    chatChannel({ id: "dev", name: "Dev-Ops" }),
    chatChannel({ id: "updates", name: "updates", purpose: "DAILY_UPDATES" }),
    chatChannel({ id: "general", name: "general" }),
  ];

  it("lists the general channels in the reader's order", () => {
    expect(chatSwitcherChannels(ordered, "").map((channel) => channel.id)).toEqual([
      "dev",
      "general",
    ]);
  });

  it("filters by name, ignoring case and spaces around the search", () => {
    expect(chatSwitcherChannels(ordered, " ops ").map((channel) => channel.id)).toEqual(["dev"]);
    expect(chatSwitcherChannels(ordered, "nothing")).toEqual([]);
  });
});

describe("chatPurposeScreenFollow", () => {
  const updates = chatChannel({ id: "u1", name: "daily", purpose: "DAILY_UPDATES" });
  const general = chatChannel({ id: "g1" });

  it("stays while a channel holds the screen's purpose", () => {
    expect(
      chatPurposeScreenFollow(ChatChannelPurpose.DAILY_UPDATES, "u1", [general, updates])
    ).toBeNull();
  });

  it("opens the shown channel in the plain channel screen once it became general", () => {
    expect(
      chatPurposeScreenFollow(ChatChannelPurpose.DAILY_UPDATES, "u1", [
        general,
        { ...updates, purpose: "GENERAL" },
      ])
    ).toEqual({ pathname: "/chat/channel/[channelId]", params: { channelId: "u1" } });
  });

  it("opens the other purpose screen when the channel took that purpose", () => {
    expect(
      chatPurposeScreenFollow(ChatChannelPurpose.DAILY_UPDATES, "u1", [
        { ...updates, purpose: "PULL_REQUESTS" },
      ])
    ).toEqual({ pathname: "/chat/pull-requests" });
    expect(
      chatPurposeScreenFollow(ChatChannelPurpose.PULL_REQUESTS, "p1", [
        chatChannel({ id: "p1", purpose: "DAILY_UPDATES" }),
      ])
    ).toEqual({ pathname: "/chat/updates" });
  });

  it("keeps the empty state for a channel that is gone, unreadable or never shown", () => {
    expect(chatPurposeScreenFollow(ChatChannelPurpose.DAILY_UPDATES, "u1", [general])).toBeNull();
    expect(
      chatPurposeScreenFollow(ChatChannelPurpose.DAILY_UPDATES, "u1", [
        { ...updates, purpose: "GENERAL", member: false },
      ])
    ).toBeNull();
    expect(chatPurposeScreenFollow(ChatChannelPurpose.DAILY_UPDATES, "", [general])).toBeNull();
    expect(chatPurposeScreenFollow(ChatChannelPurpose.DAILY_UPDATES, "u1", undefined)).toBeNull();
  });
});

describe("chatChannelScreenFollow", () => {
  const general = chatChannel({ id: "g1" });

  it("stays on the channel the screen opened on, whatever its purpose", () => {
    expect(chatChannelScreenFollow(null, general, null)).toBeNull();
    expect(
      chatChannelScreenFollow(null, { ...general, purpose: "DAILY_UPDATES" }, null)
    ).toBeNull();
    expect(
      chatChannelScreenFollow(
        { channelId: "g1", purpose: "DAILY_UPDATES" },
        { ...general, purpose: "DAILY_UPDATES" },
        null
      )
    ).toBeNull();
  });

  it("opens the purpose screen of a general channel given that purpose", () => {
    const shown = { channelId: "g1", purpose: "GENERAL" };
    expect(chatChannelScreenFollow(shown, { ...general, purpose: "DAILY_UPDATES" }, null)).toEqual({
      pathname: "/chat/updates",
    });
    expect(chatChannelScreenFollow(shown, { ...general, purpose: "PULL_REQUESTS" }, null)).toEqual({
      pathname: "/chat/pull-requests",
    });
  });

  it("opens the purpose screen of a channel switched to after its creation", () => {
    expect(
      chatChannelScreenFollow(
        { channelId: "g1", purpose: "GENERAL" },
        chatChannel({ id: "p1", purpose: "PULL_REQUESTS" }),
        null
      )
    ).toEqual({ pathname: "/chat/pull-requests" });
  });

  it("stays for a general channel, an unreadable one and an unknown one", () => {
    const shown = { channelId: "g1", purpose: "DAILY_UPDATES" };
    expect(chatChannelScreenFollow(shown, general, null)).toBeNull();
    expect(
      chatChannelScreenFollow(
        { channelId: "g1", purpose: "GENERAL" },
        { ...general, purpose: "DAILY_UPDATES", member: false },
        null
      )
    ).toBeNull();
    expect(chatChannelScreenFollow(shown, undefined, null)).toBeNull();
  });

  it("stays on a channel opened at a linked message when it is given a purpose", () => {
    const shown = { channelId: "g1", purpose: "GENERAL" };
    expect(
      chatChannelScreenFollow(shown, { ...general, purpose: "DAILY_UPDATES" }, "m1")
    ).toBeNull();
    expect(
      chatChannelScreenFollow(shown, { ...general, purpose: "PULL_REQUESTS" }, "m1")
    ).toBeNull();
  });
});
