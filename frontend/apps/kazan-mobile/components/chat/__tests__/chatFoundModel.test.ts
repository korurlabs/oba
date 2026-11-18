import { describe, expect, it } from "bun:test";

import {
  CHAT_SNIPPET_LEAD,
  chatFoundDestination,
  chatJumpStep,
  chatSearchText,
  chatSnippetParts,
} from "@/components/chat/chatFoundModel";
import type { ChatListItem } from "@/components/chat/chatListModel";
import { CHAT_SEARCH_MAX } from "@/lib/schemas/chat";

import { chatMessage } from "./chatFixtures";

describe("chatSearchText", () => {
  it("searches from two characters, trimmed", () => {
    expect(chatSearchText("  ")).toBeNull();
    expect(chatSearchText(" a ")).toBeNull();
    expect(chatSearchText(" ro ")).toBe("ro");
  });

  it("caps the text at the server's maximum", () => {
    expect(chatSearchText("x".repeat(CHAT_SEARCH_MAX + 20))).toHaveLength(CHAT_SEARCH_MAX);
  });
});

describe("chatSnippetParts", () => {
  it("marks the first case-insensitive match", () => {
    expect(chatSnippetParts("Start the Rollback now", "rollback")).toEqual({
      before: "Start the ",
      match: "Rollback",
      after: " now",
    });
  });

  it("cuts long text before the match and marks the cut", () => {
    const text = `${"a".repeat(100)}needle tail`;
    const parts = chatSnippetParts(text, "needle");
    expect(parts.before).toBe(`…${"a".repeat(CHAT_SNIPPET_LEAD)}`);
    expect(parts.match).toBe("needle");
    expect(parts.after).toBe(" tail");
  });

  it("leaves the text unmarked without a visible match", () => {
    expect(chatSnippetParts("Ping @Ayşe", "user-1")).toEqual({
      before: "Ping @Ayşe",
      match: "",
      after: "",
    });
    expect(chatSnippetParts("Pinned", null)).toEqual({ before: "Pinned", match: "", after: "" });
  });
});

describe("chatFoundDestination", () => {
  it("hands a message of the open channel to that channel's screen", () => {
    expect(chatFoundDestination({ id: "m1", channelId: "c1", threadId: "t1" }, "c1")).toEqual({
      kind: "request",
      channelId: "c1",
      threadId: "t1",
      messageId: "m1",
    });
  });

  it("pushes the channel of a message found across channels", () => {
    expect(chatFoundDestination({ id: "m1", channelId: "c2", threadId: null }, null)).toEqual({
      kind: "push",
      channelId: "c2",
      threadId: null,
      messageId: "m1",
    });
  });
});

describe("chatJumpStep", () => {
  const items: ChatListItem[] = [
    { kind: "message", key: "m2", message: chatMessage({ id: "m2" }), grouped: false },
    { kind: "day", key: "d", at: "2026-11-09T09:00:00Z" },
    { kind: "message", key: "m1", message: chatMessage({ id: "m1" }), grouped: false },
  ];

  it("scrolls to a loaded message by its row", () => {
    expect(chatJumpStep(items, "m1", null, false)).toEqual({ kind: "scroll", index: 2 });
  });

  it("goes to a thread's root above the replies", () => {
    expect(chatJumpStep(items, "root", "root", false)).toEqual({ kind: "root" });
  });

  it("loads around a message once, then gives up", () => {
    expect(chatJumpStep(items, "m9", null, false)).toEqual({ kind: "loadAround" });
    expect(chatJumpStep(items, "m9", null, true)).toEqual({ kind: "missing" });
  });
});
