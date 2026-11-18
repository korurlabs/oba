import { GlobalRegistrator } from "@happy-dom/global-registrator";
import { afterAll, beforeAll, describe, expect, it } from "bun:test";
import { Editor } from "@tiptap/core";

import {
  CODE_BLOCK_LANGUAGES,
  FORMATTABLE_LANGUAGES,
  createDescriptionExtensions,
  editorMarkdown,
  formatCode,
  imageIndexAt,
  linkTitleFromUrl,
} from "@/components/ticket/descriptionEditorExtensions";

/**
 * Parity with the web editor. Every expected string below was produced by the
 * web editor's own extension list and image serializer (copied verbatim from
 * `@oba/shared/components/markdown-editor.tsx`, node views left out) on the
 * same input, so a change on either side that alters the stored description
 * fails here.
 */

beforeAll(() => {
  GlobalRegistrator.register();
});

afterAll(async () => {
  await GlobalRegistrator.unregister();
});

function newEditor(content: string, onOpenLinkRow: () => void = () => undefined): Editor {
  return new Editor({
    extensions: createDescriptionExtensions({ placeholder: "Describe", onOpenLinkRow }),
    content,
  });
}

function roundTrip(content: string): string {
  const editor = newEditor(content);
  try {
    return editorMarkdown(editor);
  } finally {
    editor.destroy();
  }
}

const SAVED_IMAGE =
  '<img src="https://acc.blob.core.windows.net/c/a.webp?sv=1&amp;sig=x" alt="shot.png" width="1200" height="800" data-attachment-id="6f1c2b9e-1111-4222-8333-944455556666" loading="lazy" decoding="async">';

const PENDING_IMAGE =
  '<img src="kaizen-pending:3b2f" alt="IMG_0001.jpg" width="1200" height="900" loading="lazy" decoding="async">';

/** [case, stored input, what the web editor writes back]. */
const WEB_OUTPUT: readonly (readonly [string, string, string])[] = [
  ["a web-saved image stays unchanged", SAVED_IMAGE, SAVED_IMAGE],
  [
    "an image between text becomes its own block, as on the web",
    `Intro ${SAVED_IMAGE} outro`,
    `Intro\n\n${SAVED_IMAGE}\n\noutro`,
  ],
  [
    "Markdown image syntax becomes the web's <img> tag",
    "![diagram](https://example.com/d.png)",
    '<img src="https://example.com/d.png" alt="diagram" loading="lazy" decoding="async">',
  ],
  ["a pending image keeps its kaizen-pending src", PENDING_IMAGE, PENDING_IMAGE],
  [
    "attribute values are HTML-escaped",
    'Alt with "quotes" <img src="https://x.test/i.png?a=1&b=2" alt="a &quot;q&quot; &lt;b&gt;" width="10" height="20">',
    'Alt with "quotes"\n\n<img src="https://x.test/i.png?a=1&amp;b=2" alt="a &quot;q&quot; &lt;b&gt;" width="10" height="20" loading="lazy" decoding="async">',
  ],
  [
    "headings",
    "# Title\n\n## Sub\n\n### Third\n\n#### Fourth",
    "# Title\n\n## Sub\n\n### Third\n\n#### Fourth",
  ],
  [
    "bullet, nested and numbered lists stay tight",
    "- one\n- two\n  - nested\n  - nested two\n- three\n\n1. first\n2. second",
    "- one\n- two\n  - nested\n  - nested two\n- three\n\n1. first\n2. second",
  ],
  ["bullets are written with -", "* star list\n* second", "- star list\n- second"],
  [
    "task lists, nested, are written loose as on the web",
    "- [ ] todo\n- [x] done\n  - [ ] nested task",
    "- [ ] todo\n\n- [x] done\n\n  - [ ] nested task",
  ],
  [
    "quote with inline marks",
    "> quoted **bold** and *italic* and ~~gone~~ and `code`",
    "> quoted **bold** and *italic* and ~~gone~~ and `code`",
  ],
  [
    "code block with a language",
    "```typescript\nconst a: number = 1;\n```",
    "```typescript\nconst a: number = 1;\n```",
  ],
  ["a code block without a language gets json", "```\nplain\n```", "```json\nplain\n```"],
  ["table", "| A | B |\n| --- | --- |\n| 1 | 2 |", "| A | B |\n| --- | --- |\n| 1 | 2 |\n"],
  [
    "link",
    "See [the docs](https://example.com/docs) now.",
    "See [the docs](https://example.com/docs) now.",
  ],
  ["divider", "Above\n\n---\n\nBelow", "Above\n\n---\n\nBelow"],
  ["a soft line break joins the lines", "Line one\nline two", "Line one line two"],
  [
    "the block after an image starts on its own line",
    '<img src="https://x.test/a.png" alt="a" width="1" height="1">\n\n- [ ] task\n- [x] done',
    '<img src="https://x.test/a.png" alt="a" width="1" height="1" loading="lazy" decoding="async">\n\n- [ ] task\n\n- [x] done',
  ],
  ["empty", "", ""],
];

describe("createDescriptionExtensions: the web editor's stored Markdown", () => {
  for (const [name, input, expected] of WEB_OUTPUT) {
    it(name, () => {
      expect(roundTrip(input)).toBe(expected);
    });
  }

  it("reading back what it wrote gives the same text", () => {
    for (const [, , written] of WEB_OUTPUT) {
      expect(roundTrip(written)).toBe(written);
    }
  });

  it("a stored list glued to an image keeps its escaped text and is moved below the image", () => {
    expect(
      roundTrip(
        '<img src="https://x.test/a.png" alt="a" width="1" height="1" loading="lazy" decoding="async">- [ ] task\n\n- [x] done'
      )
    ).toBe(
      '<img src="https://x.test/a.png" alt="a" width="1" height="1" loading="lazy" decoding="async">\n\n\\- \\[ \\] task\n\n- [x] done'
    );
  });

  it("an image inserted at the cursor is written in the web's form", () => {
    const editor = newEditor("Before");
    editor
      .chain()
      .focus("end")
      .insertContent({
        type: "image",
        attrs: { src: "kaizen-pending:k1", alt: "IMG_0001.HEIC", width: 1200, height: 900 },
      })
      .run();
    expect(editorMarkdown(editor)).toBe(
      'Before\n\n<img src="kaizen-pending:k1" alt="IMG_0001.HEIC" width="1200" height="900" loading="lazy" decoding="async">'
    );
    editor.destroy();
  });
});

describe("/link", () => {
  function typeAndEnter(text: string, onOpenLinkRow: () => void = () => undefined): Editor {
    const editor = newEditor("", onOpenLinkRow);
    editor.chain().focus("end").insertContent(text).run();
    editor.commands.keyboardShortcut("Enter");
    return editor;
  }

  it("/link <url> becomes a link titled after the host", () => {
    const editor = typeAndEnter("See /link https://github.com/korurlabs");
    expect(editorMarkdown(editor)).toBe("See [GitHub](https://github.com/korurlabs)");
    editor.destroy();
  });

  it("/link alone is removed, keeping the space before it, and opens the link row", () => {
    let opened = 0;
    const editor = typeAndEnter("See /link", () => {
      opened++;
    });
    expect(opened).toBe(1);
    expect(editorMarkdown(editor)).toBe("See ");
    editor.destroy();
  });

  it("Return elsewhere splits the paragraph as usual", () => {
    let opened = 0;
    const editor = typeAndEnter("plain text", () => {
      opened++;
    });
    expect(opened).toBe(0);
    expect(editor.state.doc.childCount).toBe(2);
    editor.destroy();
  });
});

describe("linkTitleFromUrl", () => {
  it("names known hosts and their subdomains", () => {
    expect(linkTitleFromUrl("https://www.github.com/a")).toBe("GitHub");
    expect(linkTitleFromUrl("https://team.slack.com/archives/1")).toBe("Slack");
  });

  it("capitalizes the main part of other hosts", () => {
    expect(linkTitleFromUrl("https://docs.example.co/x")).toBe("Example");
    expect(linkTitleFromUrl("http://localhost:3000")).toBe("Localhost");
  });

  it("returns an unparseable URL as is", () => {
    expect(linkTitleFromUrl("https://")).toBe("https://");
  });
});

describe("formatCode", () => {
  it("indents JSON by two spaces", () => {
    expect(formatCode('{"a":1,"b":[1,2]}', "json")).toBe(
      '{\n  "a": 1,\n  "b": [\n    1,\n    2\n  ]\n}'
    );
  });

  it("throws on JSON that does not parse", () => {
    expect(() => formatCode("{a:", "json")).toThrow();
  });

  it("puts XML and HTML tags on indented lines", () => {
    expect(formatCode("<a><b>x</b><c/></a>", "xml")).toBe("<a>\n  <b>x</b>\n  <c/>\n</a>");
    expect(formatCode("<div><p>x</p></div>", "HTML")).toBe("<div>\n  <p>x</p>\n</div>");
  });

  it("has no formatter for other languages", () => {
    expect(formatCode("const a = 1", "typescript")).toBeNull();
  });

  it("offers the web's languages and formattable set", () => {
    expect(CODE_BLOCK_LANGUAGES.map((language) => language.value)).toEqual([
      "",
      "plaintext",
      "javascript",
      "typescript",
      "html",
      "css",
      "json",
      "xml",
      "java",
      "python",
      "sql",
      "bash",
      "yaml",
      "markdown",
    ]);
    expect([...FORMATTABLE_LANGUAGES]).toEqual(["json", "xml", "html"]);
  });
});

describe("imageIndexAt", () => {
  it("counts images in document order", () => {
    const editor = newEditor(
      `First ${SAVED_IMAGE}\n\n![b](https://x.test/b.png)\n\n- item ${PENDING_IMAGE}`
    );
    const positions: number[] = [];
    editor.state.doc.descendants((node, pos) => {
      if (node.type.name === "image") positions.push(pos);
    });
    expect(positions).toHaveLength(3);
    expect(positions.map((pos) => imageIndexAt(editor.state.doc, pos))).toEqual([0, 1, 2]);
    expect(imageIndexAt(editor.state.doc, 0)).toBe(-1);
    editor.destroy();
  });
});
