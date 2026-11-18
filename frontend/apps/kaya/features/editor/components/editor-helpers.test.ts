import { describe, expect, test } from "bun:test";

import { isMarkdownPath, languageOf } from "./editor-language";
import { splitFrontmatter } from "./frontmatter";

describe("languageOf", () => {
  test("picks the language by extension", () => {
    expect(languageOf(".agents/rules/core.md")).toBe("markdown");
    expect(languageOf(".claude/settings.json")).toBe("json");
    expect(languageOf(".agents/pipelines/ship.yaml")).toBe("yaml");
    expect(languageOf(".agents/pipelines/ship.yml")).toBe("yaml");
    expect(languageOf(".agents/skills/x/scripts/run.py")).toBe("python");
    expect(languageOf(".agents/hooks/guard.sh")).toBe("shell");
    expect(languageOf("LICENSE")).toBe("text");
  });

  test("a hook script without an extension is shell", () => {
    expect(languageOf(".agents/hooks/guard")).toBe("shell");
    expect(languageOf(".agents/rules/README")).toBe("text");
  });

  test("only Markdown offers a preview", () => {
    expect(isMarkdownPath("AGENTS.md")).toBe(true);
    expect(isMarkdownPath(".claude/settings.json")).toBe(false);
  });
});

describe("splitFrontmatter", () => {
  test("reads top-level keys and keeps the body", () => {
    const split = splitFrontmatter(
      "---\nname: core\n# a comment\npaths:\n  - src/**\n  - lib/**\nalwaysApply: true\n---\n# Core\n"
    );
    expect(split.fields).toEqual([
      { key: "name", value: "core" },
      { key: "paths", value: "- src/**\n- lib/**" },
      { key: "alwaysApply", value: "true" },
    ]);
    expect(split.body).toBe("# Core\n");
  });

  test("a file without a closed block has no fields", () => {
    expect(splitFrontmatter("# Title\n")).toEqual({ fields: null, body: "# Title\n" });
    expect(splitFrontmatter("---\nname: x\n")).toEqual({ fields: null, body: "---\nname: x\n" });
  });

  test("an empty block gives no fields and the rest as body", () => {
    expect(splitFrontmatter("---\n---\nbody")).toEqual({ fields: [], body: "body" });
  });
});

describe("lineSeparatorOf", () => {
  test("keeps CRLF for a CRLF file and LF otherwise", async () => {
    const { lineSeparatorOf } = await import("./line-separator");
    expect(lineSeparatorOf("a\r\nb")).toBe("\r\n");
    expect(lineSeparatorOf("a\nb")).toBe("\n");
    expect(lineSeparatorOf("")).toBe("\n");
  });
});
