import { describe, expect, it } from "bun:test";
import { QueryClient } from "@tanstack/react-query";

import {
  ALICE,
  CHANNEL_ID,
  ME,
  ORG_ID,
  chatMessage,
  chatThread,
} from "@/components/chat/__tests__/chatFixtures";
import {
  applyChatReaction,
  chatKeys,
  chatNarrowedListKeys,
  chatNewerPageParam,
  chatOlderPageParam,
  chatThreadsNextPageParam,
  compareChatVersions,
  findChatAttachment,
  findChatMessage,
  findChatThreadRootId,
  flattenChatMessages,
  insertChatMessage,
  isChatConversationAtNewest,
  isChatNarrowedListKey,
  patchChatMessage,
  removeChatMessage,
  replaceChatMessage,
  withChatReaderFields,
  type ChatMessageListData,
} from "@/hooks/queries/chatCache";
import {
  chatThreadPageSchema,
  type ChatAttachmentResponse,
  type ChatMessageResponse,
  type ChatThreadResponse,
} from "@/lib/schemas/chat";

function listData(
  pages: { items: ChatMessageResponse[]; hasOlder?: boolean; hasNewer?: boolean }[]
) {
  return {
    pages: pages.map((page) => ({
      items: page.items,
      hasOlder: page.hasOlder ?? false,
      hasNewer: page.hasNewer ?? false,
    })),
    pageParams: pages.map(() => ({})),
  } satisfies ChatMessageListData;
}

function clientWith(data: ChatMessageListData): QueryClient {
  const queryClient = new QueryClient();
  queryClient.setQueryData(chatKeys.channel(ORG_ID, CHANNEL_ID), data);
  return queryClient;
}

function cached(queryClient: QueryClient): ChatMessageListData | undefined {
  return queryClient.getQueryData<ChatMessageListData>(chatKeys.channel(ORG_ID, CHANNEL_ID));
}

describe("page parameters", () => {
  it("asks for the page before the oldest and after the newest message", () => {
    const page = {
      items: [chatMessage({ id: "a" }), chatMessage({ id: "b" })],
      hasOlder: true,
      hasNewer: true,
    };
    expect(chatOlderPageParam(page)).toEqual({ before: "a" });
    expect(chatNewerPageParam(page)).toEqual({ after: "b" });
    expect(chatOlderPageParam({ ...page, hasOlder: false })).toBeUndefined();
    expect(chatNewerPageParam({ ...page, hasNewer: false })).toBeUndefined();
  });
});

describe("compareChatVersions", () => {
  it("orders to the nanosecond and puts a missing version first", () => {
    expect(
      compareChatVersions("2026-11-05T10:00:00.000000002Z", "2026-11-05T10:00:00.000000001Z")
    ).toBeGreaterThan(0);
    expect(compareChatVersions(null, "2026-11-05T10:00:00Z")).toBeLessThan(0);
    expect(compareChatVersions("2026-11-05T13:00:00+03:00", "2026-11-05T10:00:00Z")).toBe(0);
  });
});

describe("insertChatMessage", () => {
  it("appends a new message to the newest page in time order", () => {
    const queryClient = clientWith(
      listData([{ items: [chatMessage({ id: "a", createdAt: "2026-11-05T10:00:00Z" })] }])
    );
    const shown = insertChatMessage(
      queryClient,
      ORG_ID,
      chatMessage({ id: "b", createdAt: "2026-11-05T10:01:00Z" })
    );
    expect(shown).toBe(true);
    expect(flattenChatMessages(cached(queryClient)).map((item) => item.id)).toEqual(["a", "b"]);
  });

  it("leaves a list scrolled into the past alone", () => {
    const queryClient = clientWith(
      listData([{ items: [chatMessage({ id: "a" })], hasNewer: true }])
    );
    expect(insertChatMessage(queryClient, ORG_ID, chatMessage({ id: "b" }))).toBe(false);
    expect(flattenChatMessages(cached(queryClient)).map((item) => item.id)).toEqual(["a"]);
    expect(isChatConversationAtNewest(queryClient, ORG_ID, CHANNEL_ID, null)).toBe(false);
  });

  it("does not add an echo of a message already shown twice", () => {
    const queryClient = clientWith(listData([{ items: [chatMessage({ id: "a" })] }]));
    insertChatMessage(queryClient, ORG_ID, chatMessage({ id: "a" }));
    expect(flattenChatMessages(cached(queryClient))).toHaveLength(1);
  });
});

describe("replaceChatMessage", () => {
  it("keeps a newer cached copy", () => {
    const newer = chatMessage({ id: "a", content: "new", version: "2026-11-05T10:00:02Z" });
    const queryClient = clientWith(listData([{ items: [newer] }]));
    replaceChatMessage(
      queryClient,
      ORG_ID,
      chatMessage({ id: "a", content: "old", version: "2026-11-05T10:00:01Z" })
    );
    expect(flattenChatMessages(cached(queryClient))[0]?.content).toBe("new");
  });
});

describe("thread roots", () => {
  const summary = {
    threadId: "t1",
    title: "Root",
    messageCount: 1,
    lastMessageAt: "2026-11-05T10:00:00Z",
    status: "ACTIVE" as const,
    memberPreviewIds: [],
    memberCount: 1,
  };

  it("finds a thread's root in a loaded channel list", () => {
    const queryClient = clientWith(
      listData([{ items: [chatMessage({ id: "root", thread: summary })] }])
    );
    expect(findChatThreadRootId(queryClient, ORG_ID, "t1")).toBe("root");
    expect(findChatThreadRootId(queryClient, ORG_ID, "t2")).toBeUndefined();
  });

  it("patches the root shown above its thread along with the channel copy", () => {
    const root = chatMessage({
      id: "root",
      content: "old",
      version: "2026-11-05T10:00:01Z",
      thread: summary,
    });
    const queryClient = clientWith(listData([{ items: [root] }]));
    queryClient.setQueryData(chatKeys.threadDetail(ORG_ID, "t1"), {
      id: "t1",
      rootMessageId: "root",
      rootMessage: root,
    });
    replaceChatMessage(queryClient, ORG_ID, {
      ...root,
      content: "new",
      version: "2026-11-05T10:00:02Z",
    });
    const detail = queryClient.getQueryData<ChatThreadResponse>(
      chatKeys.threadDetail(ORG_ID, "t1")
    );
    expect(detail?.rootMessage.content).toBe("new");
    expect(flattenChatMessages(cached(queryClient))[0]?.content).toBe("new");
  });
});

describe("removeChatMessage", () => {
  it("drops the message and marks quotes of it as deleted", () => {
    const reply = chatMessage({
      id: "b",
      replyTo: {
        messageId: "a",
        deleted: false,
        authorId: ALICE,
        snippet: "hello",
        attachments: [],
      },
    });
    const queryClient = clientWith(listData([{ items: [chatMessage({ id: "a" }), reply] }]));
    removeChatMessage(queryClient, ORG_ID, "a");
    const messages = flattenChatMessages(cached(queryClient));
    expect(messages.map((item) => item.id)).toEqual(["b"]);
    expect(messages[0]?.replyTo).toMatchObject({ deleted: true, authorId: null, snippet: "" });
  });
});

describe("flattenChatMessages", () => {
  it("lists overlapping pages once", () => {
    const a = chatMessage({ id: "a" });
    const b = chatMessage({ id: "b" });
    expect(
      flattenChatMessages(listData([{ items: [a, b] }, { items: [b] }])).map((m) => m.id)
    ).toEqual(["a", "b"]);
    expect(flattenChatMessages(undefined)).toEqual([]);
  });
});

describe("withChatReaderFields", () => {
  it("fills in the reader's mention, reply and reactions", () => {
    const message = withChatReaderFields(
      chatMessage({
        mentionedUserIds: [ME],
        replyTo: { messageId: "x", deleted: false, authorId: ME, snippet: "", attachments: [] },
        reactions: [{ emoji: "👍", count: 1, reactedByMe: false, userIds: [ME] }],
      }),
      { userId: ME, threadMember: false }
    );
    expect(message.mentionsMe).toBe(true);
    expect(message.repliesToMe).toBe(true);
    expect(message.reactions[0]?.reactedByMe).toBe(true);
  });

  it("counts a thread @everyone only for thread members", () => {
    const message = chatMessage({ threadId: "t1", mentionsEveryone: true });
    expect(withChatReaderFields(message, { userId: ME, threadMember: false }).mentionsMe).toBe(
      false
    );
    expect(withChatReaderFields(message, { userId: ME, threadMember: true }).mentionsMe).toBe(true);
  });
});

describe("applyChatReaction", () => {
  it("adds and removes a reaction and ignores an echo of the reader's own", () => {
    const added = applyChatReaction(chatMessage(), {
      emoji: "👍",
      userId: ME,
      added: true,
      isMe: true,
    });
    expect(added.reactions).toEqual([{ emoji: "👍", count: 1, reactedByMe: true, userIds: [ME] }]);
    expect(applyChatReaction(added, { emoji: "👍", userId: ME, added: true, isMe: true })).toBe(
      added
    );
    const removed = applyChatReaction(added, { emoji: "👍", userId: ME, added: false, isMe: true });
    expect(removed.reactions).toEqual([]);
  });
});

describe("thread lists", () => {
  it("asks for the threads after the last one while the server has more", () => {
    const page = { items: [chatThread({ id: "t1" }), chatThread({ id: "t2" })], hasMore: true };
    expect(chatThreadsNextPageParam(page)).toBe("t2");
    expect(chatThreadsNextPageParam({ ...page, hasMore: false })).toBeUndefined();
    expect(chatThreadsNextPageParam({ items: [], hasMore: true })).toBeUndefined();
  });

  it("keys each status of a channel under the channel's thread lists", () => {
    const key = chatKeys.threads(ORG_ID, CHANNEL_ID, "CLOSED");
    expect(key.slice(0, chatKeys.channelThreads(ORG_ID, CHANNEL_ID).length)).toEqual([
      ...chatKeys.channelThreads(ORG_ID, CHANNEL_ID),
    ]);
    expect(key.slice(0, chatKeys.threadsAll(ORG_ID).length)).toEqual([
      ...chatKeys.threadsAll(ORG_ID),
    ]);
  });

  it("reads a page of threads as the server sends it", () => {
    const page = chatThreadPageSchema.parse({ items: [chatThread()], hasMore: false });
    expect(page.items[0]?.title).toBe("Release checklist");
    expect(() => chatThreadPageSchema.parse({ items: [chatThread()] })).toThrow();
  });
});

describe("narrowed daily updates and pull requests lists", () => {
  // The keys the Daily updates and Pull requests screens list a day or a filter under.
  const updateDayKey = [...chatNarrowedListKeys.updates(ORG_ID), CHANNEL_ID, "2026-11-09", null];
  const filteredPrKey = [
    ...chatNarrowedListKeys.pullRequests(ORG_ID),
    CHANNEL_ID,
    "OPEN,APPROVED",
    ALICE,
  ];
  const photo = {
    id: "att-1",
    url: "https://storage.example/att-1?sig=x",
    fileName: "photo.png",
    contentType: "image/png",
    fileSize: 1024,
    entityType: "CHAT_MESSAGE",
    entityId: "u1",
    createdAt: "2026-11-09T09:00:00Z",
    width: null,
    height: null,
  } as ChatAttachmentResponse;

  function narrowedClient(queryKey: readonly unknown[], message: ChatMessageResponse): QueryClient {
    const queryClient = new QueryClient();
    queryClient.setQueryData(queryKey, listData([{ items: [message] }]));
    return queryClient;
  }

  function narrowedItems(queryClient: QueryClient, queryKey: readonly unknown[]) {
    return queryClient.getQueryData<ChatMessageListData>(queryKey)?.pages[0]?.items;
  }

  it("recognises only the organization's narrowed channel lists", () => {
    expect(isChatNarrowedListKey(ORG_ID, updateDayKey)).toBe(true);
    expect(isChatNarrowedListKey(ORG_ID, filteredPrKey)).toBe(true);
    expect(isChatNarrowedListKey("org-2", updateDayKey)).toBe(false);
    expect(isChatNarrowedListKey(ORG_ID, ["updates", ORG_ID, "missing", "2026-11-09"])).toBe(false);
    expect(isChatNarrowedListKey(ORG_ID, chatKeys.channel(ORG_ID, CHANNEL_ID))).toBe(false);
  });

  for (const [label, queryKey] of [
    ["an update-day list", updateDayKey],
    ["a filtered pull requests list", filteredPrKey],
  ] as const) {
    it(`finds, patches and reacts to a message held only in ${label}`, () => {
      const queryClient = narrowedClient(queryKey, chatMessage({ id: "u1", attachments: [photo] }));
      expect(findChatMessage(queryClient, ORG_ID, "u1")?.id).toBe("u1");
      expect(findChatAttachment(queryClient, ORG_ID, "u1", "att-1")?.id).toBe("att-1");

      patchChatMessage(queryClient, ORG_ID, "u1", (message) =>
        applyChatReaction(message, { emoji: "👍", userId: ME, added: true, isMe: true })
      );
      expect(narrowedItems(queryClient, queryKey)?.[0]?.reactions).toEqual([
        { emoji: "👍", count: 1, reactedByMe: true, userIds: [ME] },
      ]);

      replaceChatMessage(
        queryClient,
        ORG_ID,
        chatMessage({
          id: "u1",
          pinnedAt: "2026-11-09T10:00:00Z",
          version: "2026-11-09T10:00:00.000000001Z",
        })
      );
      expect(narrowedItems(queryClient, queryKey)?.[0]?.pinnedAt).toBe("2026-11-09T10:00:00Z");
    });

    it(`removes a deleted message from ${label}`, () => {
      const queryClient = narrowedClient(queryKey, chatMessage({ id: "u1" }));
      removeChatMessage(queryClient, ORG_ID, "u1");
      expect(narrowedItems(queryClient, queryKey)).toEqual([]);
    });

    it(`never inserts a new message into ${label}`, () => {
      const queryClient = narrowedClient(queryKey, chatMessage({ id: "u1" }));
      insertChatMessage(
        queryClient,
        ORG_ID,
        chatMessage({ id: "u2", createdAt: "2026-11-09T11:00:00Z" })
      );
      expect(narrowedItems(queryClient, queryKey)?.map((item) => item.id)).toEqual(["u1"]);
    });
  }
});
