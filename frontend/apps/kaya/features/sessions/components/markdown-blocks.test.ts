import { describe, expect, test } from "bun:test";

import { parseInline, parseMarkdown } from "./markdown-blocks";

describe("parseMarkdown", () => {
  test("reads headings, paragraphs, lists and fences", () => {
    const blocks = parseMarkdown(
      [
        "## Done so far",
        "",
        "First line",
        "second line.",
        "",
        "- one",
        "  - nested",
        "  continued",
        "- two",
        "",
        "1. first",
        "2. second",
        "",
        "```bash",
        "## not a heading",
        "- not a list",
        "```",
      ].join("\n")
    );
    expect(blocks).toEqual([
      { kind: "heading", level: 2, text: "Done so far" },
      { kind: "paragraph", text: "First line\nsecond line." },
      {
        kind: "list",
        ordered: false,
        items: [
          { text: "one", depth: 0 },
          { text: "nested continued", depth: 1 },
          { text: "two", depth: 0 },
        ],
      },
      {
        kind: "list",
        ordered: true,
        items: [
          { text: "first", depth: 0 },
          { text: "second", depth: 0 },
        ],
      },
      { kind: "code", language: "bash", text: "## not a heading\n- not a list" },
    ]);
  });

  test("an unclosed fence runs to the end", () => {
    expect(parseMarkdown("```\nopen")).toEqual([{ kind: "code", language: "", text: "open" }]);
  });

  test("HTML stays text", () => {
    expect(parseMarkdown('<script>alert("x")</script>')).toEqual([
      { kind: "paragraph", text: '<script>alert("x")</script>' },
    ]);
  });
});

describe("parseInline", () => {
  test("splits inline code and bold text", () => {
    expect(parseInline("Run `bun test` **now**, <b>ok</b>")).toEqual([
      { kind: "text", text: "Run " },
      { kind: "code", text: "bun test" },
      { kind: "text", text: " " },
      { kind: "strong", text: "now" },
      { kind: "text", text: ", <b>ok</b>" },
    ]);
  });
});
