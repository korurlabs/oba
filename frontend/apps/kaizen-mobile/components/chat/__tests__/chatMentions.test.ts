import { describe, expect, it } from "bun:test";

import {
  activeChatMentionQuery,
  activeChatTicketQuery,
  chatComposerDraft,
  chatMentionPick,
  chatMentionPickable,
  chatTicketPick,
  insertChatComposerWord,
  chatMentionSuggestions,
  chatOutboxText,
  insertChatMention,
  chatReturnTypedAt,
  serializeChatComposerText,
} from "@/components/chat/chatMentions";
import type { ChatMemberResponse } from "@/lib/schemas/chat";

import { ALICE, ME, TICKET, chatMessage } from "./chatFixtures";

function member(overrides: Partial<ChatMemberResponse> = {}): ChatMemberResponse {
  return {
    userId: ALICE,
    firstName: "Ayşe",
    lastName: "Kaya",
    email: "ayse@example.com",
    role: "MEMBER",
    online: true,
    ...overrides,
  };
}

describe("activeChatMentionQuery", () => {
  it("finds the @ word at the caret, at the start or after a space", () => {
    expect(activeChatMentionQuery("@", 1)).toEqual({ start: 0, query: "" });
    expect(activeChatMentionQuery("hi @ay", 6)).toEqual({ start: 3, query: "ay" });
  });

  it("ignores an @ inside a word, after a space, or before the caret's word", () => {
    expect(activeChatMentionQuery("mail@korur", 10)).toBeNull();
    expect(activeChatMentionQuery("@ay done", 8)).toBeNull();
    expect(activeChatMentionQuery("hi @ay", 2)).toBeNull();
  });
});

describe("insertChatMention", () => {
  it("replaces the @ word and leaves one space after the name", () => {
    const at = { start: 3, query: "ay" };
    expect(insertChatMention("hi @ay", 6, at, "Ayşe Kaya")).toEqual({
      text: "hi @Ayşe Kaya ",
      caret: 14,
    });
    expect(insertChatMention("hi @ay there", 6, at, "Ayşe Kaya").text).toBe("hi @Ayşe Kaya there");
  });
});

describe("chatMentionSuggestions", () => {
  const members = [
    member(),
    member({ userId: ME, firstName: "Mert", lastName: "Yilmaz", email: "mert@example.com" }),
  ];

  it("matches name or email and adds the groups the text starts", () => {
    expect(chatMentionSuggestions(members, "").map((item) => item.key)).toEqual([
      ALICE,
      ME,
      "everyone",
      "here",
    ]);
    expect(chatMentionSuggestions(members, "MERT@").map((item) => item.key)).toEqual([ME]);
    expect(chatMentionSuggestions(members, "he").map((item) => item.key)).toEqual(["here"]);
  });

  it("offers at most eight members", () => {
    const many = Array.from({ length: 12 }, (_, index) => member({ userId: `u${index}` }));
    expect(chatMentionSuggestions(many, "ayşe")).toHaveLength(8);
  });

  it("lists a member without Kaizen but marks them as not mentionable", () => {
    const suggestions = chatMentionSuggestions(
      [
        member({ kaizenAccess: false }),
        member({ userId: ME, firstName: "Mert", kaizenAccess: true }),
        member({ userId: "u-no-flag", firstName: "Nur" }),
      ],
      ""
    );
    expect(
      suggestions.map((item) =>
        item.kind === "member" ? [item.key, item.canMention] : [item.key, null]
      )
    ).toEqual([
      [ALICE, false],
      [ME, true],
      ["u-no-flag", false],
      ["everyone", null],
      ["here", null],
    ]);
    expect(suggestions.map(chatMentionPickable)).toEqual([false, true, false, true, true]);
  });
});

describe("serializeChatComposerText", () => {
  const ayse = chatMentionPick({ kind: "member", key: ALICE, member: member(), name: "Ayşe Kaya" });
  const here = chatMentionPick({ kind: "here", key: "here" });

  it("turns picked names into tokens, the longest first", () => {
    const ay = { display: "@Ayşe", token: `<@${ME}>` };
    expect(serializeChatComposerText("Thanks @Ayşe Kaya, cc @here", [ay, ayse, here])).toBe(
      `Thanks <@${ALICE}>, cc <@here>`
    );
  });

  it("keeps a name inside code, in a longer word, or not picked as text", () => {
    expect(serializeChatComposerText("`@Ayşe Kaya` @Ayşe Kayalar @Mert", [ayse])).toBe(
      "`@Ayşe Kaya` @Ayşe Kayalar @Mert"
    );
  });
});

describe("chatComposerDraft", () => {
  it("shows mentions by name and tickets by key, and turns them back on save", () => {
    const message = chatMessage({
      content: `Hi <@${ALICE}> and <@everyone>, see <#${TICKET}> and \`<@${ALICE}>\``,
      ticketRefs: [
        { ticketId: TICKET, boardId: "b", ticketKey: "KZN-7", ticketTitle: "Fix", canOpen: true },
      ],
    });
    const draft = chatComposerDraft(message, "Unknown");
    expect(draft.text).toBe(`Hi @Alice Akın and @everyone, see KZN-7 and \`<@${ALICE}>\``);
    expect(serializeChatComposerText(draft.text, draft.picks)).toBe(message.content);
  });

  it("keeps the token of a second user with the same name", () => {
    const message = chatMessage({
      content: `<@${ALICE}> <@${ME}>`,
      users: {
        [ALICE]: { userId: ALICE, firstName: "Ali", lastName: null, email: null, member: true },
        [ME]: { userId: ME, firstName: "Ali", lastName: null, email: null, member: true },
      },
    });
    const draft = chatComposerDraft(message, "Unknown");
    expect(draft.text).toBe(`@Ali <@${ME}>`);
    expect(serializeChatComposerText(draft.text, draft.picks)).toBe(message.content);
  });
});

describe("chatOutboxText", () => {
  it("names members and groups, and an unknown user as the unknown member", () => {
    expect(chatOutboxText(`<@${ALICE}> <@here> <@${ME}>`, [member()], "Unknown")).toBe(
      "@Ayşe Kaya @here @Unknown"
    );
  });
});

describe("ticket suggestions in the composer", () => {
  it("finds the # word at the caret, not one inside a word", () => {
    expect(activeChatTicketQuery("see #KZN", 8)).toEqual({ start: 4, query: "KZN" });
    expect(activeChatTicketQuery("#", 1)).toEqual({ start: 0, query: "" });
    expect(activeChatTicketQuery("issue#12", 8)).toBeNull();
    expect(activeChatTicketQuery("see #KZN now", 12)).toBeNull();
  });

  it("puts the ticket's key in the field and sends its token", () => {
    const pick = chatTicketPick({ id: TICKET, key: "KZN-12" });
    const inserted = insertChatComposerWord("fix #kz", 7, { start: 4, query: "kz" }, pick.display);
    expect(inserted).toEqual({ text: "fix KZN-12 ", caret: 11 });
    expect(serializeChatComposerText(inserted.text, [pick])).toBe(`fix <#${TICKET}> `);
  });
});

describe("chatReturnTypedAt", () => {
  it("finds one line break typed anywhere in the text", () => {
    expect(chatReturnTypedAt("hi @al", "hi @al\n")).toBe(6);
    expect(chatReturnTypedAt("hi @al there", "hi @al\n there")).toBe(6);
    expect(chatReturnTypedAt("", "\n")).toBe(0);
  });

  it("is null for other typing and for pasted text", () => {
    expect(chatReturnTypedAt("hi @al", "hi @ali")).toBeNull();
    expect(chatReturnTypedAt("hi", "hi\n\n")).toBeNull();
    expect(chatReturnTypedAt("hi", "h")).toBeNull();
  });
});
