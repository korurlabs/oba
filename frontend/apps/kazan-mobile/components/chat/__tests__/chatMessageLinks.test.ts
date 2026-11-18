import { describe, expect, it } from "bun:test";

import { parseChatInline } from "@/components/chat/chatMarkdown";
import { isBlankChatInlines, takeChatMessageLinks } from "@/components/chat/chatMessageLinks";

const MESSAGE = "0a1b2c3d-0000-4000-8000-000000000001";
const OTHER = "0a1b2c3d-0000-4000-8000-000000000002";
const LINK = `https://kazan.test/chats/general?jump=${MESSAGE}`;

describe("takeChatMessageLinks", () => {
  it("takes a bare message link out of the text and keeps the words around it", () => {
    const { inlines, links } = takeChatMessageLinks(parseChatInline(`see ${LINK} please`));
    expect(links.map((link) => link.messageId)).toEqual([MESSAGE]);
    expect(inlines).toEqual([
      { type: "text", text: "see " },
      { type: "text", text: " please" },
    ]);
  });

  it("keeps a labelled link and any other URL as links", () => {
    const text = `[the plan](${LINK}) and https://example.com/a`;
    const { inlines, links } = takeChatMessageLinks(parseChatInline(text));
    expect(links).toEqual([]);
    expect(inlines).toEqual(parseChatInline(text));
  });

  it("finds links inside emphasis and lists each message once", () => {
    const second = `https://kazan.test/daily-updates?thread=${OTHER}&threadMessage=${MESSAGE}`;
    const { inlines, links } = takeChatMessageLinks(
      parseChatInline(`**${LINK}** ${LINK} ${second}`)
    );
    expect(links.map((link) => link.href)).toEqual([LINK]);
    expect(isBlankChatInlines(inlines)).toBe(true);
  });
});

describe("isBlankChatInlines", () => {
  it("is true only for white space", () => {
    expect(isBlankChatInlines([{ type: "text", text: "  " }])).toBe(true);
    expect(isBlankChatInlines([])).toBe(true);
    expect(isBlankChatInlines(parseChatInline("a"))).toBe(false);
    expect(isBlankChatInlines(parseChatInline("`x`"))).toBe(false);
  });
});
