import { describe, expect, it } from "bun:test";

import {
  chatScreenRequestThreadId,
  clearChatScreenRequest,
  pendingChatScreenRequest,
  requestChatScreen,
  type ChatScreenRequest,
} from "@/components/chat/chatScreenRequests";

describe("chatScreenRequests", () => {
  it("keeps only the newest request and clears only the one acted on", () => {
    const first: ChatScreenRequest = {
      kind: "reply",
      channelId: "c1",
      threadId: null,
      messageId: "m1",
    };
    const second: ChatScreenRequest = { kind: "switch", channelId: "c1", toChannelId: "c2" };
    requestChatScreen(first);
    requestChatScreen(second);
    expect(pendingChatScreenRequest()).toBe(second);
    clearChatScreenRequest(first);
    expect(pendingChatScreenRequest()).toBe(second);
    clearChatScreenRequest(second);
    expect(pendingChatScreenRequest()).toBeNull();
  });

  it("sends a reply or edit to its thread's screen and everything else to the channel's", () => {
    expect(
      chatScreenRequestThreadId({ kind: "edit", channelId: "c1", threadId: "t1", messageId: "m1" })
    ).toBe("t1");
    expect(
      chatScreenRequestThreadId({ kind: "reply", channelId: "c1", threadId: null, messageId: "m1" })
    ).toBeNull();
    expect(
      chatScreenRequestThreadId({ kind: "openThread", channelId: "c1", threadId: "t1" })
    ).toBeNull();
    expect(chatScreenRequestThreadId({ kind: "newChannel", channelId: "c1" })).toBeNull();
    expect(
      chatScreenRequestThreadId({ kind: "manageMembers", channelId: "c1", membersChannelId: "c2" })
    ).toBeNull();
    // A jump into a thread is taken by the channel, which opens the thread over itself.
    expect(
      chatScreenRequestThreadId({ kind: "jump", channelId: "c1", threadId: "t1", messageId: "m1" })
    ).toBeNull();
  });
});
