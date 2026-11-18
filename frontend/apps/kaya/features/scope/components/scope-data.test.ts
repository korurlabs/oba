import { describe, expect, test } from "bun:test";

import type { RepositoryResponse } from "@/features/sessions/schemas";

import type { ReleaseDetailResponse } from "@/features/releases/schemas";
import type { VersionDetailResponse } from "@/features/versions/schemas";
import {
  frontmatterList,
  frontmatterText,
  parseFrontmatter,
  parseSettings,
} from "@/features/versions/components/file-parsers";

import type { Owner } from "../schemas";
import {
  filesForTab,
  filesFromRelease,
  filesFromVersion,
  skillFiles,
  skillsFromFiles,
  type ScopeFile,
} from "./scope-files";
import {
  editTarget,
  parseScope,
  resolveScope,
  scopeOfOwner,
  scopeOptions,
  scopeValue,
} from "./scope-data";

function repository(id: string, name: string, status = "ACTIVE"): RepositoryResponse {
  return { id, name, remoteUrl: `git@github.com:org/${name}.git`, status };
}

function owner(overrides: Partial<Owner> & Pick<Owner, "id" | "name">): Owner {
  return {
    parentPackId: null,
    currentVersionId: null,
    required: false,
    mountPaths: [],
    repositoryIds: [],
    ...overrides,
  };
}

const OWNERS: Owner[] = [
  owner({ id: "k-org", name: "org", repositoryIds: ["r-1", "r-2"] }),
  owner({ id: "k-village", name: "village", repositoryIds: ["r-1"] }),
  owner({
    id: "k-web",
    name: "web",
    parentPackId: "k-village",
    mountPaths: ["apps/web"],
    repositoryIds: ["r-3"],
  }),
];

function file(path: string, overrides: Partial<ScopeFile> = {}): ScopeFile {
  return {
    path,
    mode: "FILE",
    sha256: "a".repeat(64),
    size: 10,
    target: null,
    alwaysOn: null,
    versionId: "v-1",
    ownerId: "k-org",
    ...overrides,
  };
}

describe("scope", () => {
  test("round-trips through the URL value", () => {
    expect(parseScope(scopeValue({ kind: "REPOSITORY", repositoryId: "r-1" }))).toEqual({
      kind: "REPOSITORY",
      repositoryId: "r-1",
    });
    expect(parseScope("pack:k-org")).toEqual({ kind: "PACK", packId: "k-org" });
    expect(scopeValue({ kind: "PACK", packId: "k-web" })).toBe("pack:k-web");
    expect(parseScope("pack:")).toBeNull();
    expect(parseScope("project:p-1")).toBeNull();
    expect(parseScope("workspace:r-1")).toBeNull();
    expect(parseScope(null)).toBeNull();
  });

  test("offers active repositories and every pack, child packs included, by name", () => {
    const options = scopeOptions(
      [
        repository("r-2", "kagi"),
        repository("r-1", "village"),
        repository("r-3", "old", "ARCHIVED"),
      ],
      OWNERS
    );
    expect(options.repositories.map((option) => option.label)).toEqual(["kagi", "village"]);
    expect(options.packs.map((option) => option.value)).toEqual([
      "pack:k-org",
      "pack:k-village",
      "pack:k-web",
    ]);
    expect("projects" in options).toBe(false);
  });

  test("falls back to the first pack, then the first repository", () => {
    const options = scopeOptions([repository("r-1", "village")], OWNERS);
    expect(resolveScope("pack:k-village", options)).toEqual({ kind: "PACK", packId: "k-village" });
    expect(resolveScope("pack:k-web", options)).toEqual({ kind: "PACK", packId: "k-web" });
    expect(resolveScope("pack:gone", options)).toEqual({ kind: "PACK", packId: "k-org" });
    expect(resolveScope("project:p-1-web", options)).toEqual({ kind: "PACK", packId: "k-org" });
    expect(resolveScope(null, scopeOptions([repository("r-1", "village")], []))).toEqual({
      kind: "REPOSITORY",
      repositoryId: "r-1",
    });
    expect(resolveScope(null, scopeOptions([], OWNERS))).toEqual({
      kind: "PACK",
      packId: "k-org",
    });
    expect(resolveScope(null, scopeOptions([], []))).toBeNull();
  });

  test("a pack edits itself, a repository is read only", () => {
    expect(editTarget({ kind: "PACK", packId: "k-org" }, OWNERS)?.id).toBe("k-org");
    expect(editTarget({ kind: "PACK", packId: "k-web" }, OWNERS)?.id).toBe("k-web");
    expect(editTarget({ kind: "PACK", packId: "gone" }, OWNERS)).toBeNull();
    expect(editTarget({ kind: "REPOSITORY", repositoryId: "r-1" }, OWNERS)).toBeNull();
  });

  test("a pack's own scope is its pack scope, root or child", () => {
    const [root, , child] = OWNERS as [Owner, Owner, Owner];
    expect(scopeOfOwner(root)).toEqual({ kind: "PACK", packId: "k-org" });
    expect(scopeOfOwner(child)).toEqual({ kind: "PACK", packId: "k-web" });
  });
});

describe("scope files", () => {
  test("a release entry reads its content from the version that delivered it", () => {
    const release: ReleaseDetailResponse = {
      id: "rel",
      repositoryId: "r-1",
      number: 3,
      causeVersionId: "v-9",
      manifestSha256: "b".repeat(64),
      keyId: "k",
      fileCount: 1,
      createdAt: "2026-10-23T09:00:00Z",
      signature: "sig",
      packVersions: [{ packId: "p-1", versionId: "v-7", rootPath: null }],
      files: [
        {
          path: "AGENTS.md",
          mode: "FILE",
          size: 10,
          alwaysOn: true,
          ownerId: "p-1",
          versionId: "v-7",
        },
      ],
    };
    expect(filesFromRelease(release)[0]).toMatchObject({
      path: "AGENTS.md",
      alwaysOn: true,
      versionId: "v-7",
      ownerId: "p-1",
    });
  });

  test("a pack version's files carry no always-on flag", () => {
    const version = {
      id: "v-2",
      packId: "k-org",
      files: [{ path: ".agents/rules/core.md", mode: "FILE", symlinkTarget: null }],
    } as unknown as VersionDetailResponse;
    expect(filesFromVersion(version)[0]).toMatchObject({
      alwaysOn: null,
      versionId: "v-2",
      ownerId: "k-org",
    });
  });

  test("skills come from SKILL.md files that are not symlinks", () => {
    const files = [
      file(".agents/skills/kazan/SKILL.md"),
      file(".agents/skills/kazan/references/cli.md"),
      file(".agents/skills/nested/deeper/SKILL.md"),
      file(".claude/skills/kazan", { mode: "SYMLINK", target: "../../.agents/skills/kazan" }),
      file(".claude/skills/plugin/SKILL.md"),
    ];
    const skills = skillsFromFiles(files);
    expect(skills.map((skill) => [skill.directoryName, skill.directory, skill.fileCount])).toEqual([
      ["kazan", ".agents/skills/kazan", 2],
      ["plugin", ".claude/skills/plugin", 1],
    ]);
    expect(skillFiles(files, ".agents/skills/kazan").map((entry) => entry.path)).toEqual([
      ".agents/skills/kazan/SKILL.md",
      ".agents/skills/kazan/references/cli.md",
    ]);
  });

  test("convention tabs pick their directories", () => {
    const files = [
      file(".agents/AGENTS.md"),
      file(".agents/hooks/pre-bash-destructive-git.sh"),
      file(".agents/rules/core.md"),
      file(".agents/rules-old.md"),
      file(".claude/agents/coding-engineer.md"),
      file("AGENTS.md"),
    ];
    expect(filesForTab(files, "rules").map((entry) => entry.path)).toEqual([
      ".agents/rules/core.md",
    ]);
    expect(filesForTab(files, "agents-md").map((entry) => entry.path)).toEqual([
      ".agents/AGENTS.md",
      "AGENTS.md",
    ]);
    expect(filesForTab(files, "subagents")).toHaveLength(1);
    expect(filesForTab(files, "hooks")).toHaveLength(1);
    expect(filesForTab(files, "templates")).toHaveLength(0);
  });

});

describe("frontmatter", () => {
  test("reads scalars, folded text and lists", () => {
    const content = [
      "---",
      "name: kazan",
      'description: "Use for any Kazan ticket work"',
      "when_to_use: >",
      "  Triggers on ticket",
      "  and sprint.",
      "paths:",
      "  - '**/*.java'",
      "  - src/**",
      "globs: [a.ts, 'b.ts']",
      "---",
      "# Body",
    ].join("\n");
    const frontmatter = parseFrontmatter(content);
    expect(frontmatterText(frontmatter, "name")).toBe("kazan");
    expect(frontmatterText(frontmatter, "description")).toBe("Use for any Kazan ticket work");
    expect(frontmatterText(frontmatter, "when_to_use")).toBe("Triggers on ticket and sprint.");
    expect(frontmatterList(frontmatter, "paths")).toEqual(["**/*.java", "src/**"]);
    expect(frontmatterList(frontmatter, "globs")).toEqual(["a.ts", "b.ts"]);
    expect(frontmatterList(frontmatter, "missing")).toBeNull();
  });

  test("a folded or literal block runs past its blank lines", () => {
    const content = [
      "---",
      "description: >",
      "  First paragraph",
      "  goes on.",
      "",
      "  Second paragraph.",
      "when_to_use: |",
      "  line one",
      "",
      "    indented line",
      "",
      "name: kazan",
      "paths:",
      "  - a/**",
      "",
      "  - b/**",
      "---",
      "# Body",
    ].join("\n");
    const frontmatter = parseFrontmatter(content);
    expect(frontmatterText(frontmatter, "description")).toBe(
      "First paragraph goes on.\nSecond paragraph."
    );
    expect(frontmatterText(frontmatter, "when_to_use")).toBe("line one\n\n  indented line");
    expect(frontmatterText(frontmatter, "name")).toBe("kazan");
    expect(frontmatterList(frontmatter, "paths")).toEqual(["a/**", "b/**"]);
  });

  test("content without a closed block has no frontmatter", () => {
    expect(parseFrontmatter("# Title\nname: x")).toEqual({});
    expect(parseFrontmatter("---\nname: x\n")).toEqual({});
  });
});

describe("settings", () => {
  test("reads the permission lists and every hook command", () => {
    const parsed = parseSettings(
      JSON.stringify({
        permissions: { allow: ["Bash(git status)", 3], deny: ["Read(./.env)"] },
        hooks: {
          PreToolUse: [
            { matcher: "Bash", hooks: [{ type: "command", command: ".agents/hooks/dispatch.sh" }] },
          ],
          SessionStart: [{ hooks: [{ type: "command", command: "kaya hook session-start" }] }],
        },
      })
    );
    expect(parsed.allow).toEqual(["Bash(git status)"]);
    expect(parsed.ask).toEqual([]);
    expect(parsed.deny).toEqual(["Read(./.env)"]);
    expect(parsed.hooks).toEqual([
      {
        event: "PreToolUse",
        matcher: "Bash",
        type: "command",
        command: ".agents/hooks/dispatch.sh",
      },
      { event: "SessionStart", matcher: null, type: "command", command: "kaya hook session-start" },
    ]);
  });

  test("invalid JSON throws", () => {
    expect(() => parseSettings("{ not json")).toThrow();
  });
});
