import { describe, expect, it } from "bun:test";
import { QueryClient } from "@tanstack/react-query";

import { CHANNEL_ID, ME, ORG_ID, chatMessage } from "@/components/chat/__tests__/chatFixtures";
import {
  chatKeys,
  chatNarrowedListKeys,
  findChatMessage,
  insertChatMessage,
  type ChatMessageListData,
} from "@/hooks/queries/chatCache";
import { planChatMessageCreated } from "@/hooks/queries/chatMessageSignals";
import type { ChatMessageResponse, ChatMessageSignal } from "@/lib/schemas/chat";

const CONVERSATION = { channelId: CHANNEL_ID, threadId: null };
const NARROWED_PULL_REQUESTS = [
  ...chatNarrowedListKeys.pullRequests(ORG_ID),
  CHANNEL_ID,
  "OPEN",
  null,
] as const;
const NARROWED_UPDATES_DAY = [
  ...chatNarrowedListKeys.updates(ORG_ID),
  CHANNEL_ID,
  "2026-11-05",
  null,
] as const;

function listData(items: ChatMessageResponse[], hasNewer: boolean = false): ChatMessageListData {
  return {
    pages: [{ items, hasOlder: false, hasNewer }],
    pageParams: [{}],
  };
}

function createdSignal(message: ChatMessageResponse): ChatMessageSignal {
  return {
    type: "MESSAGE_CREATED",
    messageId: message.id,
    threadId: null,
    authorId: message.author?.userId ?? null,
    createdAt: message.createdAt,
    clientNonce: null,
    version: message.version,
    message,
  };
}

function channelIds(queryClient: QueryClient): string[] {
  const data: ChatMessageListData | undefined = queryClient.getQueryData<ChatMessageListData>(
    chatKeys.channel(ORG_ID, CHANNEL_ID)
  );
  return (data?.pages ?? []).flatMap((page) => page.items.map((item) => item.id));
}

describe("planChatMessageCreated", () => {
  it("inserts a message a narrowed list refetched first into the chat list", () => {
    const older: ChatMessageResponse = chatMessage({ id: "signal-old" });
    const share: ChatMessageResponse = chatMessage({
      id: "signal-share",
      createdAt: "2026-11-05T10:05:00.000Z",
    });
    const queryClient = new QueryClient();
    queryClient.setQueryData(chatKeys.channel(ORG_ID, CHANNEL_ID), listData([older]));
    queryClient.setQueryData(NARROWED_PULL_REQUESTS, listData([older, share]));

    // The organization-wide lookup sees the narrowed copy; the plan must not.
    expect(findChatMessage(queryClient, ORG_ID, share.id)?.id).toBe(share.id);
    const plan = planChatMessageCreated(
      queryClient,
      ORG_ID,
      CONVERSATION,
      createdSignal(share),
      ME
    );
    expect(plan.decision.kind).toBe("apply");
    expect(plan.insert).toBe(true);

    if (plan.decision.kind !== "apply") throw new Error("expected the payload to be applied");
    insertChatMessage(queryClient, ORG_ID, plan.decision.message);
    expect(channelIds(queryClient)).toEqual([older.id, share.id]);
  });

  it("ignores a daily updates day that holds the message too", () => {
    const update: ChatMessageResponse = chatMessage({ id: "signal-update" });
    const queryClient = new QueryClient();
    queryClient.setQueryData(chatKeys.channel(ORG_ID, CHANNEL_ID), listData([]));
    queryClient.setQueryData(NARROWED_UPDATES_DAY, listData([update]));

    const plan = planChatMessageCreated(
      queryClient,
      ORG_ID,
      CONVERSATION,
      createdSignal(update),
      ME
    );
    expect(plan.decision.kind).toBe("apply");
    expect(plan.insert).toBe(true);
  });

  it("drops a message the chat list already holds at the same version", () => {
    const message: ChatMessageResponse = chatMessage({ id: "signal-loaded" });
    const queryClient = new QueryClient();
    queryClient.setQueryData(chatKeys.channel(ORG_ID, CHANNEL_ID), listData([message]));

    const plan = planChatMessageCreated(
      queryClient,
      ORG_ID,
      CONVERSATION,
      createdSignal(message),
      ME
    );
    expect(plan.decision.kind).toBe("drop");
  });

  it("does not insert into a chat list scrolled into the past, even with a narrowed copy", () => {
    const share: ChatMessageResponse = chatMessage({ id: "signal-past" });
    const queryClient = new QueryClient();
    queryClient.setQueryData(chatKeys.channel(ORG_ID, CHANNEL_ID), listData([], true));
    queryClient.setQueryData(NARROWED_PULL_REQUESTS, listData([share]));

    const plan = planChatMessageCreated(
      queryClient,
      ORG_ID,
      CONVERSATION,
      createdSignal(share),
      ME
    );
    expect(plan.decision.kind).toBe("apply");
    expect(plan.insert).toBe(false);
  });
});
