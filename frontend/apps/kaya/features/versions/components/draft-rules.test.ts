import { describe, expect, test } from "bun:test";

import type { Owner } from "@/features/scope/schemas";
import type { VersionDetailResponse } from "../schemas";
import { composeDraftFiles, type ScopeFile } from "@/features/scope/components/scope-files";
import {
  checkFile,
  isAllowedPath,
  lineCount,
  listingLength,
  measureOf,
  newSkillContent,
  skillNameIssue,
} from "./draft-rules";
import { editTarget, scopeOfOwner } from "@/features/scope/components/scope-data";

const rules = (issues: { rule: string }[]) => issues.map((issue) => issue.rule);

describe("isAllowedPath", () => {
  test("takes the delivered paths", () => {
    for (const path of [
      "AGENTS.md",
      ".agents/AGENTS.md",
      ".claude/settings.json",
      ".agents/rules/core.md",
      ".agents/skills/kazan/SKILL.md",
      ".agents/hooks/pre-bash-git.sh",
      ".claude/agents/coder.md",
      ".claude/skills/x/run.py",
      "docs/private/conventions/backend/32-soft-delete-convention.md",
      "docs/private/conventions/a.md",
    ]) {
      expect(isAllowedPath(path)).toBe(true);
    }
  });

  test("refuses other folders, escapes, empty segments and backslashes", () => {
    for (const path of [
      "",
      "README.md",
      ".agents/docs/x.md",
      ".claude/commands/plan.md",
      ".agents/rules/",
      "docs/private/conventions/",
      "docs/private/conventions/.md",
      "docs/private/conventions/a.png",
      "docs/private/conventions/a.txt",
      "docs/private/conventions/../a.md",
      "docs/private/conventions//a.md",
      "docs/private/a.md",
      "docs/other/a.md",
      ".agents/rules/../x.md",
      ".agents/rules/./x.md",
      ".agents/rules//x.md",
      ".agents/rules\\x.md",
      `.agents/rules/${"a".repeat(500)}`,
    ]) {
      expect(isAllowedPath(path)).toBe(false);
    }
  });
});

describe("checkFile", () => {
  const skill = (body: string) => ({
    path: ".agents/skills/k/SKILL.md",
    mode: "FILE" as const,
    content: body,
  });

  test("a path off the allow-list is the only issue", () => {
    expect(rules(checkFile({ path: "docs/x.md", mode: "FILE", content: "x" }, null))).toEqual([
      "PATH_NOT_ALLOWED",
    ]);
  });

  test("a new file over 5 MiB is refused", () => {
    const big = "a".repeat(5_242_881);
    expect(
      rules(checkFile({ path: ".agents/rules/a.md", mode: "FILE", content: big }, null))
    ).toEqual(["FILE_TOO_LARGE"]);
  });

  test("a file the base already held over a limit may stay as large, not grow", () => {
    const big = "a".repeat(5_242_900);
    const base = measureOf(big);
    const path = ".agents/rules/a.md";
    expect(checkFile({ path, mode: "FILE", content: big }, base)).toEqual([]);
    expect(checkFile({ path, mode: "FILE", content: big.slice(1) }, base)).toEqual([]);
    expect(rules(checkFile({ path, mode: "FILE", content: `${big}a` }, base))).toEqual([
      "FILE_TOO_LARGE",
    ]);
  });

  test("a base under the limit gives no waiver", () => {
    const path = ".agents/rules/a.md";
    expect(
      rules(checkFile({ path, mode: "FILE", content: "a".repeat(5_242_881) }, measureOf("small")))
    ).toEqual(["FILE_TOO_LARGE"]);
  });

  test("an unknown base measure is not refused before it loads", () => {
    expect(
      checkFile(
        { path: ".agents/rules/a.md", mode: "FILE", content: "a".repeat(5_242_881) },
        undefined
      )
    ).toEqual([]);
  });

  test("a skill of 1500 lines is too long unless it was and did not grow", () => {
    const long = `---\nname: k\n---\n${"line\n".repeat(1497)}`;
    expect(lineCount(long)).toBe(1500);
    expect(rules(checkFile(skill(long), null))).toEqual(["SKILL_TOO_LONG"]);
    expect(checkFile(skill(long), measureOf(long))).toEqual([]);
    expect(rules(checkFile(skill(`${long}more\n`), measureOf(long)))).toEqual(["SKILL_TOO_LONG"]);
  });

  test("unknown frontmatter keys and an unclosed block are named", () => {
    expect(rules(checkFile(skill("---\nname: k\ncolour: red\n---\n"), null))).toEqual([
      "FRONTMATTER_KEY_UNKNOWN",
    ]);
    expect(rules(checkFile(skill("---\nname: k\n"), null))).toEqual(["FRONTMATTER_INVALID"]);
    expect(checkFile(skill("---\nname: k\ndescription: d\nwhen_to_use: w\n---\n"), null)).toEqual(
      []
    );
  });

  test("description and when_to_use over 1536 code points are too long", () => {
    const text = `---\ndescription: ${"é".repeat(1000)}\nwhen_to_use: ${"x".repeat(537)}\n---\n`;
    expect(listingLength(text)).toBe(1537);
    expect(rules(checkFile(skill(text), null))).toEqual(["SKILL_DESCRIPTION_TOO_LONG"]);
  });

  test("the settings file must be a JSON object", () => {
    const settings = (content: string) => ({
      path: ".claude/settings.json",
      mode: "FILE" as const,
      content,
    });
    expect(rules(checkFile(settings("[1]"), null))).toEqual(["SETTINGS_INVALID_JSON"]);
    expect(rules(checkFile(settings("{"), null))).toEqual(["SETTINGS_INVALID_JSON"]);
    expect(checkFile(settings('{"permissions": {}}'), null)).toEqual([]);
  });
});

describe("skillNameIssue", () => {
  test("one folder name that is not taken", () => {
    expect(skillNameIssue("kazan", [])).toBeNull();
    expect(skillNameIssue("", [])).not.toBeNull();
    expect(skillNameIssue("a/b", [])).not.toBeNull();
    expect(skillNameIssue("..", [])).not.toBeNull();
    expect(skillNameIssue("kazan", ["kazan"])).not.toBeNull();
  });

  test("the template passes the skill checks", () => {
    expect(
      checkFile(
        {
          path: ".agents/skills/kazan/SKILL.md",
          mode: "FILE",
          content: newSkillContent("kazan"),
        },
        null
      )
    ).toEqual([]);
  });
});

function file(path: string, ownerId: string, extra: Partial<ScopeFile> = {}): ScopeFile {
  return {
    path,
    mode: "FILE",
    sha256: `sha-${path}`,
    size: 100,
    target: null,
    alwaysOn: null,
    versionId: "v",
    ownerId,
    ...extra,
  };
}

describe("the draft in its scope", () => {
  test("composeDraftFiles puts the draft in place of its owner, keeping known flags", () => {
    const draft = {
      id: "d",
      packId: "own",
      files: [
        { path: ".agents/rules/a.md", sha256: "sha-.agents/rules/a.md", mode: "FILE", size: 1 },
        { path: ".agents/rules/c.md", sha256: "new", mode: "FILE", size: 1 },
      ],
    } as unknown as VersionDetailResponse;
    const composed = composeDraftFiles(
      [
        file("AGENTS.md", "pack"),
        file(".agents/rules/a.md", "own", { alwaysOn: true }),
        file(".agents/rules/b.md", "own"),
      ],
      "own",
      draft
    );
    expect(
      composed.map((entry) => [entry.path, entry.ownerId, entry.versionId, entry.alwaysOn])
    ).toEqual([
      [".agents/rules/a.md", "own", "d", true],
      [".agents/rules/c.md", "own", "d", null],
      ["AGENTS.md", "pack", "v", null],
    ]);
  });
});

describe("editTarget", () => {
  const base = { currentVersionId: null, required: false, mountPaths: [], repositoryIds: [] };
  const owners: Owner[] = [
    { ...base, id: "p", name: "org", parentPackId: null },
    { ...base, id: "w", name: "village", parentPackId: "p" },
  ];

  test("a pack edits itself, a repository nothing, an unknown pack nothing", () => {
    expect(editTarget({ kind: "PACK", packId: "p" }, owners)?.id).toBe("p");
    expect(editTarget({ kind: "PACK", packId: "w" }, owners)?.id).toBe("w");
    expect(editTarget({ kind: "REPOSITORY", repositoryId: "r-1" }, owners)).toBeNull();
    expect(editTarget({ kind: "PACK", packId: "gone" }, owners)).toBeNull();
  });

  test("a pack's own scope", () => {
    expect(scopeOfOwner(owners[0] as Owner)).toEqual({ kind: "PACK", packId: "p" });
    expect(scopeOfOwner(owners[1] as Owner)).toEqual({ kind: "PACK", packId: "w" });
  });
});
