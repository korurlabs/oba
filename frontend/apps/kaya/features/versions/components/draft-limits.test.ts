import { describe, expect, test } from "bun:test";

import { draftRebaseRequestSchema } from "../schemas";

import {
  FILE_LIMITS,
  checkFile,
  isAllowedPath,
  lineCount,
  measureOf,
  textBytes,
} from "./draft-rules";

const rules = (issues: { rule: string }[]) => issues.map((issue) => issue.rule);
const RULE = ".agents/rules/a.md";
const SKILL = ".agents/skills/k/SKILL.md";
const file = (path: string, content: string) => ({ path, mode: "FILE" as const, content });

describe("the size limits sit exactly on their edge", () => {
  test("5 MiB is accepted, one byte more is refused", () => {
    expect(checkFile(file(RULE, "a".repeat(FILE_LIMITS.fileBytesMax)), null)).toEqual([]);
    expect(rules(checkFile(file(RULE, "a".repeat(FILE_LIMITS.fileBytesMax + 1)), null))).toEqual([
      "FILE_TOO_LARGE",
    ]);
  });

  test("the limit counts UTF-8 bytes, not characters", () => {
    const text = "é".repeat(2_621_441);
    expect(text.length).toBeLessThan(FILE_LIMITS.fileBytesMax);
    expect(textBytes(text)).toBe(5_242_882);
    expect(rules(checkFile(file(RULE, text), null))).toEqual(["FILE_TOO_LARGE"]);
  });

  test("a skill of 1499 lines passes and one of 1500 does not", () => {
    const lines = (count: number) => `---\nname: k\n---\n${"x\n".repeat(count - 3)}`;
    expect(lineCount(lines(1499))).toBe(1499);
    expect(checkFile(file(SKILL, lines(1499)), null)).toEqual([]);
    expect(rules(checkFile(file(SKILL, lines(1500)), null))).toEqual(["SKILL_TOO_LONG"]);
  });

  test("a listing of exactly 1536 code points passes", () => {
    const text = `---\ndescription: ${"x".repeat(1000)}\nwhen_to_use: ${"y".repeat(536)}\n---\n`;
    expect(checkFile(file(SKILL, text), null)).toEqual([]);
  });

  test("a path of 500 characters passes and one of 501 does not", () => {
    const prefix = ".agents/rules/";
    expect(isAllowedPath(prefix + "a".repeat(500 - prefix.length))).toBe(true);
    expect(isAllowedPath(prefix + "a".repeat(501 - prefix.length))).toBe(false);
  });
});

describe("a limit binds only a file that is added or grows", () => {
  test("a base file over the limit may shrink but stays waived only while it does not grow", () => {
    const big = "a".repeat(FILE_LIMITS.fileBytesMax + 100);
    const base = measureOf(big);
    expect(checkFile(file(RULE, big.slice(0, -50)), base)).toEqual([]);
    expect(checkFile(file(RULE, big), base)).toEqual([]);
    expect(rules(checkFile(file(RULE, big + "a"), base))).toEqual(["FILE_TOO_LARGE"]);
  });

  test("a base file that shrank under the limit is bound again", () => {
    const base = measureOf("a".repeat(FILE_LIMITS.fileBytesMax));
    expect(rules(checkFile(file(RULE, "a".repeat(FILE_LIMITS.fileBytesMax + 1)), base))).toEqual([
      "FILE_TOO_LARGE",
    ]);
  });

  test("a new file has no waiver, whatever else the draft holds", () => {
    const long = `---\nname: k\n---\n${"x\n".repeat(1600)}`;
    expect(rules(checkFile(file(SKILL, long), null))).toEqual(["SKILL_TOO_LONG"]);
  });

  test("an over-long listing is waived while it does not grow", () => {
    const text = (length: number) => `---\nname: k\ndescription: ${"x".repeat(length)}\n---\n`;
    const base = measureOf(text(1600));
    expect(checkFile(file(SKILL, text(1600)), base)).toEqual([]);
    expect(checkFile(file(SKILL, text(1550)), base)).toEqual([]);
    expect(rules(checkFile(file(SKILL, text(1601)), base))).toEqual(["SKILL_DESCRIPTION_TOO_LONG"]);
  });

  test("an oversize file reports only its size, not the skill rules behind it", () => {
    const text = `---\nname: k\ncolour: red\n---\n${"x".repeat(FILE_LIMITS.fileBytesMax)}`;
    expect(rules(checkFile(file(SKILL, text), null))).toEqual(["FILE_TOO_LARGE"]);
  });
});

describe("the rule syntax", () => {
  test("a CRLF frontmatter is closed and its keys are read", () => {
    expect(checkFile(file(SKILL, "---\r\nname: k\r\ndescription: d\r\n---\r\nbody"), null)).toEqual(
      []
    );
    expect(rules(checkFile(file(SKILL, "---\r\nname: k\r\ncolour: red\r\n---\r\n"), null))).toEqual(
      ["FRONTMATTER_KEY_UNKNOWN"]
    );
  });

  test("a file without frontmatter is not a frontmatter error", () => {
    expect(checkFile(file(SKILL, "# Just text\n"), null)).toEqual([]);
  });

  test("every unknown key is named once", () => {
    const issues = checkFile(file(SKILL, "---\nfoo: 1\nbar: 2\nname: k\n---\n"), null);
    expect(issues.map((issue) => issue.message)).toEqual([
      "The frontmatter key foo is not a skill key.",
      "The frontmatter key bar is not a skill key.",
    ]);
  });

  test("a settings file that is an empty string, null or a string is refused", () => {
    for (const content of ["", "null", '"x"', "1"]) {
      expect(
        rules(checkFile(file(".claude/settings.json", content), null)),
        JSON.stringify(content)
      ).toEqual(["SETTINGS_INVALID_JSON"]);
    }
    expect(checkFile(file(".claude/settings.json", "{}"), null)).toEqual([]);
  });

  test("a path with spaces around it is trimmed before the allow-list", () => {
    expect(checkFile(file(` ${RULE} `, "x"), null)).toEqual([]);
  });
});

describe("the rebase choices sent to the server", () => {
  test("a path may be kept from the draft or taken from the current version, not both", () => {
    expect(
      draftRebaseRequestSchema.safeParse({ keepDraft: ["a"], takeCurrent: ["b"] }).success
    ).toBe(true);
    expect(
      draftRebaseRequestSchema.safeParse({ keepDraft: ["a"], takeCurrent: ["a"] }).success
    ).toBe(false);
  });

  test("no choices at all is a valid rebase", () => {
    expect(draftRebaseRequestSchema.safeParse({}).success).toBe(true);
    expect(draftRebaseRequestSchema.safeParse({ keepDraft: [], takeCurrent: [] }).success).toBe(
      true
    );
  });
});
