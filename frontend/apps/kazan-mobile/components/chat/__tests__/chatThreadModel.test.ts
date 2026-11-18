import { describe, expect, it } from "bun:test";

import {
  CHAT_THREAD_ACTION_STATUS,
  CHAT_THREAD_TABS,
  chatChannelThreadMarks,
  chatChannelUnreadThreads,
  chatMessageThreadMentions,
  chatThreadDefaultTitle,
  chatThreadListMarks,
  chatThreadMenuActions,
  chatThreadSettingsChanged,
  chatThreadSettingsDraft,
  chatThreadSummaryPeople,
  chatThreadTitleProblem,
  chatThreadUpdateRequest,
} from "@/components/chat/chatThreadModel";
import type { ChatConversationUnreadResponse, ChatUnread } from "@/lib/schemas/chat";

import { ALICE, CHANNEL_ID, ME, chatMessage, chatThread } from "./chatFixtures";

describe("chatThreadDefaultTitle", () => {
  it("cuts the root's text at 40 characters", () => {
    const title = chatThreadDefaultTitle(chatMessage({ content: "a".repeat(60) }), "Unknown");
    expect(title).toBe(`${"a".repeat(40)}…`);
    expect(chatThreadDefaultTitle(chatMessage({ content: "Short **text**" }), "Unknown")).toBe(
      "Short text"
    );
  });

  it("starts empty for a daily update, which the server titles", () => {
    expect(
      chatThreadDefaultTitle(
        chatMessage({
          content: "",
          dailyUpdateId: "u1",
          dailyUpdate: { summaryText: "Did things" },
        }),
        "Unknown"
      )
    ).toBe("");
  });

  it("takes the first line of a shared pull request's summary", () => {
    const share = chatMessage({
      content: "",
      pullRequestShareId: "p1",
      pullRequestShare: { summaryText: "KZN-1: Fix the login\nmore" },
    });
    expect(chatThreadDefaultTitle(share, "Unknown")).toBe("KZN-1: Fix the login");
  });
});

describe("chatThreadMenuActions", () => {
  it("offers join or leave to a writer and nothing more without manage rights", () => {
    expect(
      chatThreadMenuActions({ status: "ACTIVE", member: false, canManage: false }, true)
    ).toEqual(["join"]);
    expect(
      chatThreadMenuActions({ status: "ACTIVE", member: true, canManage: false }, true)
    ).toEqual(["leave"]);
    expect(
      chatThreadMenuActions({ status: "ACTIVE", member: true, canManage: false }, false)
    ).toEqual([]);
  });

  it("offers the status changes the server allows from each status", () => {
    const manage = { member: true, canManage: true };
    expect(chatThreadMenuActions({ ...manage, status: "ACTIVE" }, true)).toEqual([
      "leave",
      "edit",
      "close",
      "archive",
    ]);
    expect(chatThreadMenuActions({ ...manage, status: "CLOSED" }, true)).toEqual([
      "leave",
      "edit",
      "reopen",
    ]);
    expect(chatThreadMenuActions({ ...manage, status: "ARCHIVED" }, true)).toEqual([
      "leave",
      "edit",
      "unarchive",
      "close",
    ]);
    expect(CHAT_THREAD_ACTION_STATUS.reopen).toBe("ACTIVE");
    expect(CHAT_THREAD_ACTION_STATUS.archive).toBe("ARCHIVED");
  });
});

describe("chatThreadMenuActions edit", () => {
  const statuses = ["ACTIVE", "CLOSED", "ARCHIVED"] as const;

  it("offers Edit thread first among the managing actions in every status", () => {
    for (const status of statuses) {
      for (const canWrite of [true, false]) {
        const actions = chatThreadMenuActions({ status, member: false, canManage: true }, canWrite);
        expect(actions.filter((id) => id === "edit")).toHaveLength(1);
        expect(actions.indexOf("edit")).toBe(canWrite ? 1 : 0);
      }
    }
  });

  it("never offers Edit thread without manage rights", () => {
    for (const status of statuses) {
      for (const member of [true, false]) {
        for (const canWrite of [true, false]) {
          expect(
            chatThreadMenuActions({ status, member, canManage: false }, canWrite)
          ).not.toContain("edit");
        }
      }
    }
  });
});

describe("chatThreadSettingsDraft", () => {
  it("opens with the thread's title and archive window", () => {
    expect(chatThreadSettingsDraft({ title: "Release", autoArchiveMinutes: 4320 })).toEqual({
      title: "Release",
      autoArchiveMinutes: 4320,
    });
  });

  it("shows one week for a window the settings do not offer", () => {
    expect(chatThreadSettingsDraft({ title: "Old", autoArchiveMinutes: 2880 })).toEqual({
      title: "Old",
      autoArchiveMinutes: 10080,
    });
  });
});

describe("chatThreadTitleProblem", () => {
  it("needs a name that is not blank", () => {
    expect(chatThreadTitleProblem("")).toBe("required");
    expect(chatThreadTitleProblem("   ")).toBe("required");
  });

  it("allows 100 characters after trimming and no more", () => {
    expect(chatThreadTitleProblem(`  ${"a".repeat(100)}  `)).toBeNull();
    expect(chatThreadTitleProblem("a".repeat(101))).toBe("tooLong");
  });
});

describe("chatThreadUpdateRequest", () => {
  it("sends the trimmed name and the window together", () => {
    expect(chatThreadUpdateRequest({ title: "  Release  ", autoArchiveMinutes: 60 })).toEqual({
      title: "Release",
      autoArchiveMinutes: 60,
    });
  });
});

describe("chatThreadSettingsChanged", () => {
  const initial = { title: "Release", autoArchiveMinutes: 10080 } as const;

  it("is unchanged while saving would send the opening values", () => {
    expect(chatThreadSettingsChanged(initial, { ...initial })).toBe(false);
    expect(chatThreadSettingsChanged(initial, { ...initial, title: " Release " })).toBe(false);
  });

  it("is changed by a new name or a new window", () => {
    expect(chatThreadSettingsChanged(initial, { ...initial, title: "Release 2" })).toBe(true);
    expect(chatThreadSettingsChanged(initial, { ...initial, autoArchiveMinutes: 4320 })).toBe(true);
    expect(chatThreadSettingsChanged(initial, { ...initial, title: "" })).toBe(true);
  });
});

function threadUnread(
  id: string,
  unread: number,
  mentionCount: number,
  channelId: string = CHANNEL_ID
): ChatConversationUnreadResponse {
  return { id, channelId, unread, mentionCount, lastReadMessageId: null, lastReadAt: null };
}

function unreadWith(threads: ChatConversationUnreadResponse[]): ChatUnread {
  return { channels: [], threads, total: 0, mentionTotal: 0 };
}

describe("CHAT_THREAD_TABS", () => {
  it("lists Active, Closed and Archived in that order", () => {
    expect(CHAT_THREAD_TABS).toEqual(["ACTIVE", "CLOSED", "ARCHIVED"]);
  });
});

describe("chatThreadListMarks", () => {
  const unread = unreadWith([threadUnread("t1", 4, 1)]);

  it("marks an active thread with its unread replies and notifications", () => {
    expect(chatThreadListMarks(chatThread(), unread)).toEqual({ unread: 4, mentions: 1 });
  });

  it("marks nothing for a thread without read state or before the counts load", () => {
    expect(chatThreadListMarks(chatThread({ id: "t2" }), unread)).toEqual({
      unread: 0,
      mentions: 0,
    });
    expect(chatThreadListMarks(chatThread(), undefined)).toEqual({ unread: 0, mentions: 0 });
  });

  it("never marks a closed or archived thread", () => {
    expect(chatThreadListMarks(chatThread({ status: "CLOSED" }), unread)).toEqual({
      unread: 0,
      mentions: 0,
    });
    expect(chatThreadListMarks(chatThread({ status: "ARCHIVED" }), unread)).toEqual({
      unread: 0,
      mentions: 0,
    });
  });
});

describe("chatChannelUnreadThreads", () => {
  it("counts the channel's threads with unread replies, with or without a notification", () => {
    const unread = unreadWith([
      threadUnread("t1", 4, 1),
      threadUnread("t2", 2, 0),
      threadUnread("t3", 0, 0),
      threadUnread("t4", 9, 5, "other-channel"),
    ]);
    expect(chatChannelUnreadThreads(unread, CHANNEL_ID)).toBe(2);
    expect(chatChannelUnreadThreads(unread, "other-channel")).toBe(1);
  });

  it("is zero once every thread of the channel is read", () => {
    expect(chatChannelUnreadThreads(unreadWith([threadUnread("t1", 0, 0)]), CHANNEL_ID)).toBe(0);
  });

  it("is zero without counts or a channel", () => {
    expect(chatChannelUnreadThreads(undefined, CHANNEL_ID)).toBe(0);
    expect(chatChannelUnreadThreads(unreadWith([threadUnread("t1", 1, 1)]), null)).toBe(0);
  });
});

describe("chatChannelThreadMarks", () => {
  it("adds up the unread replies and notifications of the channel's threads only", () => {
    const unread = unreadWith([
      threadUnread("t1", 4, 1),
      threadUnread("t2", 2, 0),
      threadUnread("t3", 0, 0),
      threadUnread("t4", 9, 5, "other-channel"),
    ]);
    expect(chatChannelThreadMarks(unread, CHANNEL_ID)).toEqual({ unread: 6, mentions: 1 });
    expect(chatChannelThreadMarks(unread, "other-channel")).toEqual({ unread: 9, mentions: 5 });
  });

  it("marks nothing for a channel without unread threads", () => {
    const unread = unreadWith([threadUnread("t1", 3, 0, "other-channel")]);
    expect(chatChannelThreadMarks(unread, CHANNEL_ID)).toEqual({ unread: 0, mentions: 0 });
  });

  it("marks nothing without counts or a channel", () => {
    expect(chatChannelThreadMarks(undefined, CHANNEL_ID)).toEqual({ unread: 0, mentions: 0 });
    expect(chatChannelThreadMarks(unreadWith([threadUnread("t1", 1, 1)]), null)).toEqual({
      unread: 0,
      mentions: 0,
    });
  });
});

describe("chatThreadSummaryPeople", () => {
  const users = chatMessage().users;

  it("names the preview members the page carries and counts every member", () => {
    expect(
      chatThreadSummaryPeople(
        { memberPreviewIds: [ALICE.toUpperCase(), ME], memberCount: 5 },
        users,
        "Unknown"
      )
    ).toEqual({ names: ["Alice Akın"], total: 5 });
  });

  it("never counts fewer members than it names", () => {
    expect(
      chatThreadSummaryPeople({ memberPreviewIds: [ALICE], memberCount: 0 }, users, "Unknown")
    ).toEqual({ names: ["Alice Akın"], total: 1 });
  });
});

describe("chatMessageThreadMentions", () => {
  const summary = {
    threadId: "t1",
    title: "Root",
    messageCount: 2,
    lastMessageAt: "2026-11-01T10:00:00Z",
    status: "ACTIVE" as const,
    memberPreviewIds: [],
    memberCount: 1,
  };
  const unread: ChatUnread = {
    channels: [],
    threads: [
      {
        id: "t1",
        channelId: CHANNEL_ID,
        unread: 3,
        mentionCount: 2,
        lastReadMessageId: null,
        lastReadAt: null,
      },
    ],
    total: 3,
    mentionTotal: 2,
  };

  it("reads the notifications of the message's active thread", () => {
    expect(chatMessageThreadMentions(chatMessage({ thread: summary }), unread)).toBe(2);
  });

  it("is zero without a thread, for a closed thread and before the counts load", () => {
    expect(chatMessageThreadMentions(chatMessage(), unread)).toBe(0);
    expect(
      chatMessageThreadMentions(chatMessage({ thread: { ...summary, status: "CLOSED" } }), unread)
    ).toBe(0);
    expect(chatMessageThreadMentions(chatMessage({ thread: summary }), undefined)).toBe(0);
  });
});
