import { describe, expect, it } from "bun:test";

import {
  chatAuthorName,
  chatMessageSnippet,
  chatMentionTone,
  chatMessageText,
  chatPersonName,
  chatReactionPeople,
  isOwnChatMessage,
} from "@/components/chat/chatDisplay";

import { ALICE, ME, TICKET, chatMessage } from "./chatFixtures";

describe("chatPersonName and chatAuthorName", () => {
  it("joins first and last name and falls back to the email", () => {
    expect(chatPersonName({ firstName: "Alice", lastName: "Akın", email: "a@x.io" })).toBe(
      "Alice Akın"
    );
    expect(chatPersonName({ firstName: null, lastName: null, email: "a@x.io" })).toBe("a@x.io");
  });

  it("names a message without author as the unknown member", () => {
    expect(chatAuthorName(chatMessage({ author: null }), "Unknown")).toBe("Unknown");
    expect(chatAuthorName(chatMessage(), "Unknown")).toBe("Alice Akın");
  });
});

describe("chatMessageText", () => {
  it("renders mentions and ticket keys, but not inside code", () => {
    const message = chatMessage({
      content: `<@${ALICE}> <@here> <#${TICKET}> \`<@${ALICE}>\` <@${ME}>`,
      ticketRefs: [
        { ticketId: TICKET, boardId: "b", ticketKey: "KAI-7", ticketTitle: "Fix", canOpen: true },
      ],
    });
    expect(chatMessageText(message, "Unknown")).toBe(
      `@Alice Akın @here KAI-7 \`<@${ALICE}>\` @Unknown`
    );
  });

  it("uses a card entry's summary", () => {
    const message = chatMessage({
      content: "",
      dailyUpdateId: "du",
      dailyUpdate: { summaryText: "Done: X" },
    });
    expect(chatMessageText(message, "Unknown")).toBe("Done: X");
  });
});

describe("chatMessageSnippet", () => {
  it("strips markers and joins lines", () => {
    const message = chatMessage({ content: "**bold**\n```ts\ncode\n```\n<https://x.io>" });
    expect(chatMessageSnippet(message, "Unknown", 80)).toBe("bold code https://x.io");
  });

  it("drops single emphasis markers, list markers and link targets but keeps snake_case", () => {
    const message = chatMessage({
      content:
        "**Bold** and _italic_ *text*, a list:\n- first\n- [docs](https://x.io) for snake_case",
    });
    expect(chatMessageSnippet(message, "Unknown", 80)).toBe(
      "Bold and italic text, a list: first docs for snake_case"
    );
  });

  it("keeps a token inside code literal", () => {
    const message = chatMessage({ content: "`<@here>` and <@here>" });
    expect(chatMessageSnippet(message, "Unknown", 80)).toBe("<@here> and @here");
  });
});

describe("isOwnChatMessage", () => {
  it("is true only for the reader's own message, ignoring the ID's case", () => {
    const mine = chatMessage({
      author: { userId: ME.toUpperCase(), email: "me@example.com" },
    });
    expect(isOwnChatMessage(mine, ME)).toBe(true);
    expect(isOwnChatMessage(chatMessage(), ME)).toBe(false);
  });

  it("is false before the reader is known and for a message without an author", () => {
    expect(isOwnChatMessage(chatMessage(), undefined)).toBe(false);
    expect(isOwnChatMessage(chatMessage({ author: null }), ME)).toBe(false);
  });
});

describe("chatMentionTone", () => {
  const member = { member: true };

  it("highlights the reader's own mention and a member's is a plain chip", () => {
    expect(chatMentionTone(ME.toUpperCase(), member, ME, false)).toBe("me");
    expect(chatMentionTone(ALICE, member, ME, false)).toBe("member");
  });

  it("mutes a user who is not a member", () => {
    expect(chatMentionTone(ALICE, { member: false }, ME, false)).toBe("unknown");
    expect(chatMentionTone(ALICE, undefined, ME, false)).toBe("unknown");
  });

  it("highlights @everyone and @here only when the message notifies the reader", () => {
    expect(chatMentionTone("everyone", undefined, ME, true)).toBe("me");
    expect(chatMentionTone("here", undefined, ME, false)).toBe("member");
  });
});

describe("chatReactionPeople", () => {
  const message = chatMessage();

  it("names the listed users and counts the rest", () => {
    expect(
      chatReactionPeople({ count: 12, userIds: [ALICE, ME] }, message.users, "Unknown")
    ).toEqual({ names: ["Alice Akın", "Unknown"], others: 10 });
    expect(chatReactionPeople({ count: 1, userIds: [ALICE] }, message.users, "Unknown")).toEqual({
      names: ["Alice Akın"],
      others: 0,
    });
  });
});
