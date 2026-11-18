import { describe, expect, test } from "bun:test";

import {
  RULE_TAB_IDS,
  RULE_TABS,
  filesForTab,
  formatBytes,
  skillDirectoryOf,
  skillsFromFiles,
  type RuleTab,
  type ScopeFile,
} from "./scope-files";
import { parseFrontmatter, parseSettings } from "@/features/versions/components/file-parsers";
import { parseScope, scopeValue } from "./scope-data";

function file(path: string): ScopeFile {
  return {
    path,
    mode: "FILE",
    sha256: null,
    size: 1,
    target: null,
    alwaysOn: null,
    versionId: "v",
    ownerId: "s",
  };
}

/** Every directory prefix the backend allow-list (PathRules.ALLOWED_PATH) accepts. */
const TAB_OF_PREFIX: [string, RuleTab | null][] = [
  ["AGENTS.md", "agents-md"],
  [".agents/AGENTS.md", "agents-md"],
  [".agents/rules/core.md", "rules"],
  [".agents/roles/coding-engineer.md", "roles"],
  [".agents/workflows/plan.md", "workflows"],
  [".agents/templates/HANDOFF_TEMPLATE.md", "templates"],
  [".agents/hooks/pre-bash-x.sh", "hooks"],
  [".claude/agents/coding-engineer.md", "subagents"],
  // Skills, the settings file and the convention docs have their own pages.
  [".agents/skills/kazan/SKILL.md", null],
  [".claude/skills/kazan/SKILL.md", null],
  [".claude/settings.json", null],
  ["docs/private/conventions/backend/a.md", null],
];

describe("rule tab assignment", () => {
  test("every allowed prefix lands in exactly one tab, or on its own page", () => {
    for (const [path, expected] of TAB_OF_PREFIX) {
      const tabs = RULE_TAB_IDS.filter(
        (tab: RuleTab) => filesForTab([file(path)], tab).length === 1
      );
      expect(tabs).toEqual(expected === null ? [] : [expected]);
    }
  });

  test("a nested path stays in its tab and a lookalike directory does not", () => {
    expect(filesForTab([file(".agents/rules/sub/deep.md")], "rules")).toHaveLength(1);
    expect(filesForTab([file(".agents/rules")], "rules")).toHaveLength(0);
    expect(filesForTab([file(".agents/rulesets/a.md")], "rules")).toHaveLength(0);
    expect(filesForTab([file("a/b/c/d/e/AGENTS.md")], "agents-md")).toHaveLength(0);
    expect(filesForTab([file("docs/AGENTS.mdx")], "agents-md")).toHaveLength(0);
    // A folder's AGENTS.md is the agents file of a pack mounted there, delivered beside the root one.
    expect(filesForTab([file("projects/kagi/AGENTS.md")], "agents-md")).toHaveLength(1);
  });

  test("the tab list has the seven ids in display order, each once", () => {
    expect(RULE_TAB_IDS).toEqual([
      "rules",
      "agents-md",
      "roles",
      "subagents",
      "workflows",
      "templates",
      "hooks",
    ]);
    expect(new Set(RULE_TABS.map((tab) => tab.label)).size).toBe(7);
  });

  test("tab files keep the scope's path order", () => {
    const files = [file(".agents/rules/b.md"), file(".agents/rules/a.md")];
    expect(filesForTab(files, "rules").map((entry) => entry.path)).toEqual([
      ".agents/rules/b.md",
      ".agents/rules/a.md",
    ]);
  });
});

describe("skill detection", () => {
  test("only a SKILL.md directly under a skill directory counts", () => {
    expect(skillDirectoryOf(".agents/skills/kazan/SKILL.md")).toBe(".agents/skills/kazan");
    expect(skillDirectoryOf(".claude/skills/x/SKILL.md")).toBe(".claude/skills/x");
    expect(skillDirectoryOf(".agents/skills/a/b/SKILL.md")).toBeNull();
    expect(skillDirectoryOf(".agents/skills/x/skill.md")).toBeNull();
    expect(skillDirectoryOf(".agents/skills/SKILL.md")).toBeNull();
    expect(skillDirectoryOf(".agents/rules/x/SKILL.md")).toBeNull();
    expect(skillDirectoryOf("x/.agents/skills/a/SKILL.md")).toBeNull();
  });

  test("a symlinked SKILL.md is not a skill", () => {
    const link = { ...file(".agents/skills/a/SKILL.md"), mode: "SYMLINK" as const };
    expect(skillsFromFiles([link])).toEqual([]);
  });

  test("the file count includes only the skill's own directory, not a lookalike", () => {
    const skills = skillsFromFiles([
      file(".agents/skills/a/SKILL.md"),
      file(".agents/skills/a/x.md"),
      file(".agents/skills/ab/SKILL.md"),
    ]);
    expect(skills.map((skill) => skill.fileCount)).toEqual([2, 1]);
  });
});

describe("byte formatting", () => {
  test("bytes are written with thousands separators", () => {
    expect(formatBytes(27000)).toBe("27,000 bytes");
    expect(formatBytes(0)).toBe("0 bytes");
  });
});

describe("scope param", () => {
  test("building then parsing returns the same scope, ids with colons included", () => {
    const repository = { kind: "REPOSITORY", repositoryId: "a:b" } as const;
    expect(scopeValue(repository)).toBe("repository:a:b");
    expect(parseScope(scopeValue(repository))).toEqual(repository);
    expect(parseScope("pack:x:y")).toEqual({ kind: "PACK", packId: "x:y" });
  });

  test("malformed values give null", () => {
    for (const value of ["", "repository:", "Repository:r", " pack:p", "repository", "p:pack"]) {
      expect(parseScope(value)).toBeNull();
    }
    expect(parseScope(undefined)).toBeNull();
  });
});

describe("frontmatter edge cases", () => {
  test("handles CRLF line endings", () => {
    const frontmatter = parseFrontmatter("---\r\nname: x\r\ndescription: y\r\n---\r\nbody");
    expect(frontmatter).toEqual({ name: "x", description: "y" });
  });

  test("a literal block keeps its lines and a quoted value loses its quotes", () => {
    const frontmatter = parseFrontmatter(
      ["---", "notes: |", "  one", "  two", "title: 'it works'", "---"].join("\n")
    );
    expect(frontmatter).toEqual({ notes: "one\ntwo", title: "it works" });
  });

  test("a key with no value is left out and an empty inline list is empty", () => {
    const frontmatter = parseFrontmatter(["---", "empty:", "list: []", "---"].join("\n"));
    expect(frontmatter).toEqual({ list: [] });
  });

  test("an empty file gives no frontmatter", () => {
    expect(parseFrontmatter("")).toEqual({});
  });
});

describe("settings edge cases", () => {
  test("a settings file that is not an object gives empty lists", () => {
    for (const content of ["[]", "null", "3", '"x"']) {
      expect(parseSettings(content)).toEqual({ allow: [], ask: [], deny: [], hooks: [] });
    }
  });

  test("malformed hook groups are skipped, not thrown on", () => {
    const parsed = parseSettings(
      JSON.stringify({ hooks: { Stop: "x", PreToolUse: [{ hooks: "y" }, 4, { hooks: [{}] }] } })
    );
    expect(parsed.hooks).toEqual([
      { event: "PreToolUse", matcher: null, type: null, command: null },
    ]);
  });
});
