import { describe, expect, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

import { Markdown } from "./markdown";
import { parseInline, parseMarkdown, safeHref, tableCells } from "./markdown-blocks";

describe("parseMarkdown", () => {
  test("reads headings, lists and fences as before", () => {
    expect(parseMarkdown("## Title\n\n- one\n  - two\n\n```sh\n# not a heading\n```")).toEqual([
      { kind: "heading", level: 2, text: "Title" },
      {
        kind: "list",
        ordered: false,
        items: [
          { text: "one", depth: 0 },
          { text: "two", depth: 1 },
        ],
      },
      { kind: "code", language: "sh", text: "# not a heading" },
    ]);
  });

  test("reads a block quote with its own blocks", () => {
    expect(parseMarkdown("> **Note**\n> - a\n\nafter")).toEqual([
      {
        kind: "quote",
        blocks: [
          { kind: "paragraph", text: "**Note**" },
          { kind: "list", ordered: false, items: [{ text: "a", depth: 0 }] },
        ],
      },
      { kind: "paragraph", text: "after" },
    ]);
  });

  test("reads a pipe table with its alignment and pads short rows", () => {
    expect(parseMarkdown("| Kind | Path |\n|:---|---:|\n| rule | `a\\|b` |\n| hook |")).toEqual([
      {
        kind: "table",
        header: ["Kind", "Path"],
        align: ["left", "right"],
        rows: [
          ["rule", "`a|b`"],
          ["hook", ""],
        ],
      },
    ]);
  });

  test("a line with a pipe but no separator row stays a paragraph", () => {
    expect(parseMarkdown("a | b\nc")).toEqual([{ kind: "paragraph", text: "a | b\nc" }]);
  });
});

describe("parseInline", () => {
  test("reads links beside code and bold text", () => {
    expect(parseInline("See [core](../core.md) and `x` **y**")).toEqual([
      { kind: "text", text: "See " },
      { kind: "link", text: "core", href: "../core.md" },
      { kind: "text", text: " and " },
      { kind: "code", text: "x" },
      { kind: "text", text: " " },
      { kind: "strong", text: "y" },
    ]);
  });

  test("splits table cells", () => {
    expect(tableCells("| a | b |")).toEqual(["a", "b"]);
  });
});

describe("safeHref", () => {
  test("only web and mail addresses get an address", () => {
    expect(safeHref("https://example.com")).toBe("https://example.com");
    expect(safeHref("mailto:a@b.c")).toBe("mailto:a@b.c");
    expect(safeHref("javascript:alert(1)")).toBeNull();
    expect(safeHref("../rules/core.md")).toBeNull();
  });
});

describe("Markdown", () => {
  test("shows raw HTML as text", () => {
    const html = renderToStaticMarkup(<Markdown source={'<script>alert(1)</script>\n\n<b>x</b>'} />);
    expect(html).not.toContain("<script>");
    expect(html).toContain("&lt;script&gt;");
    expect(html).toContain("&lt;b&gt;x&lt;/b&gt;");
  });

  test("a javascript link renders without an address", () => {
    const html = renderToStaticMarkup(<Markdown source="[x](javascript:alert(1))" />);
    expect(html).not.toContain("href=");
  });

  test("renders tables and quotes", () => {
    const html = renderToStaticMarkup(<Markdown source={"> q\n\n| a |\n|---|\n| 1 |"} />);
    expect(html).toContain("<blockquote");
    expect(html).toContain("<table");
    expect(html).toContain("<td");
  });
});
