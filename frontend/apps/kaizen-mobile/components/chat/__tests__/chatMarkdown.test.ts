import { describe, expect, it } from "bun:test";

import {
  CHAT_CODE_COLLAPSED_LINES,
  chatCodeView,
  isSafeChatUrl,
  parseChatInline,
  parseChatMarkdown,
} from "@/components/chat/chatMarkdown";

import { ME, TICKET } from "./chatFixtures";

describe("parseChatInline", () => {
  it("reads bold, italic, strike and inline code", () => {
    expect(parseChatInline("a **b** _c_ ~~d~~ `e`")).toEqual([
      { type: "text", text: "a " },
      { type: "bold", children: [{ type: "text", text: "b" }] },
      { type: "text", text: " " },
      { type: "italic", children: [{ type: "text", text: "c" }] },
      { type: "text", text: " " },
      { type: "strike", children: [{ type: "text", text: "d" }] },
      { type: "text", text: " " },
      { type: "code", text: "e" },
    ]);
  });

  it("links a markdown link and an autolink, leaving trailing punctuation out", () => {
    expect(parseChatInline("see [docs](https://x.io) and https://y.io/path. ok")).toEqual([
      { type: "text", text: "see " },
      { type: "link", url: "https://x.io", children: [{ type: "text", text: "docs" }] },
      { type: "text", text: " and " },
      {
        type: "link",
        url: "https://y.io/path",
        children: [{ type: "text", text: "https://y.io/path" }],
      },
      { type: "text", text: ". ok" },
    ]);
  });

  it("keeps a link with an unsafe scheme as text", () => {
    expect(parseChatInline("[bad](javascript:alert(1))")).toEqual([
      { type: "text", text: "[bad](javascript:alert(1))" },
    ]);
  });

  it("turns mention and ticket tokens into chips", () => {
    expect(parseChatInline(`hi <@everyone> <@${ME}> <#${TICKET}>`)).toEqual([
      { type: "text", text: "hi " },
      { type: "mention", target: "everyone" },
      { type: "text", text: " " },
      { type: "mention", target: ME },
      { type: "text", text: " " },
      { type: "ticket", ticketId: TICKET },
    ]);
  });

  it("drops the backslash of an escaped marker", () => {
    expect(parseChatInline("\\*not\\*")).toEqual([{ type: "text", text: "*not*" }]);
  });
});

describe("parseChatMarkdown", () => {
  it("splits code, quotes, lists and paragraphs, keeping tokens in code literal", () => {
    const content =
      "```ts\nconst a = `<@everyone>`;\n```\n> quoted\n> more\n- one\n- two\n\n3. three\npara";
    expect(parseChatMarkdown(content)).toEqual([
      { type: "code", language: "ts", code: "const a = `<@everyone>`;" },
      {
        type: "quote",
        blocks: [{ type: "paragraph", inlines: [{ type: "text", text: "quoted\nmore" }] }],
      },
      {
        type: "list",
        ordered: false,
        start: 1,
        items: [[{ type: "text", text: "one" }], [{ type: "text", text: "two" }]],
      },
      { type: "list", ordered: true, start: 3, items: [[{ type: "text", text: "three" }]] },
      { type: "paragraph", inlines: [{ type: "text", text: "para" }] },
    ]);
  });

  it("runs an unclosed fence to the end of the text", () => {
    expect(parseChatMarkdown("```\nopen")).toEqual([
      { type: "code", language: null, code: "open" },
    ]);
  });
});

describe("isSafeChatUrl", () => {
  it("allows only web and mail links", () => {
    expect(isSafeChatUrl("https://x.io")).toBe(true);
    expect(isSafeChatUrl("mailto:a@b.io")).toBe(true);
    expect(isSafeChatUrl("javascript:alert(1)")).toBe(false);
    expect(isSafeChatUrl("data:text/html,x")).toBe(false);
  });
});

describe("chatCodeView", () => {
  const lines = (count: number): string =>
    Array.from({ length: count }, (_, index) => `line ${index + 1}`).join("\n");

  it("shows a short block whole, with nothing to expand", () => {
    expect(chatCodeView(lines(CHAT_CODE_COLLAPSED_LINES), false)).toEqual({
      text: lines(CHAT_CODE_COLLAPSED_LINES),
      lineCount: CHAT_CODE_COLLAPSED_LINES,
      collapsible: false,
    });
  });

  it("shows the first 20 lines of a longer block until it is expanded", () => {
    const code: string = lines(25);
    expect(chatCodeView(code, false)).toEqual({
      text: lines(CHAT_CODE_COLLAPSED_LINES),
      lineCount: 25,
      collapsible: true,
    });
    expect(chatCodeView(code, true).text).toBe(code);
  });
});
