import { describe, expect, test } from "bun:test";

import { mountNewFileFolder } from "@/features/conventions/components/convention-files";
import { skillOrigin } from "@/features/skills/components/skill-rows";
import {
  checkFile,
  isAllowedPath,
  skillNameIssue,
} from "@/features/versions/components/draft-rules";

import type { Owner } from "../schemas";
import {
  AGENTS_FILE,
  classifyPattern,
  expandBraces,
  isInMountNamespace,
  mountDocsFolder,
  mountNamespaceMessage,
  mountRefOf,
  mountRuleRefusal,
  mountTabStart,
  rulePatterns,
  type MountRef,
} from "./mount-paths";
import { fileOrigins, filesForTab, type ScopeFile } from "./scope-files";

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

const KAGI: MountRef = { name: "kagi", mountPaths: ["projects/kagi"] };

const OWNERS: Owner[] = [
  owner({ id: "p-org", name: "org" }),
  owner({ id: "s-kazan", name: "kazan", parentPackId: "p-org", mountPaths: ["projects/kazan"] }),
  owner({ id: "s-kagi", name: "kagi", parentPackId: "p-org", mountPaths: ["projects/kagi"] }),
];

function file(path: string, ownerId: string, extra: Partial<ScopeFile> = {}): ScopeFile {
  return {
    path,
    mode: "FILE",
    size: 10,
    sha256: null,
    target: null,
    alwaysOn: false,
    versionId: `v-${ownerId}`,
    ownerId,
    ...extra,
  } as ScopeFile;
}

describe("a pack as a mount", () => {
  test("a pack mounted in a folder has a namespace, one used at roots only has none", () => {
    expect(mountRefOf(OWNERS[2])).toEqual({ name: "kagi", mountPaths: ["projects/kagi"] });
    expect(mountRefOf(OWNERS[0])).toBeNull();
    expect(mountRefOf(undefined)).toBeNull();
    expect(mountRefOf(null)).toBeNull();
  });

  test("a pack mounted in two repositories keeps every folder", () => {
    const twice = owner({ id: "x", name: "web", mountPaths: ["apps/web", "web"] });
    expect(mountRefOf(twice)?.mountPaths).toEqual(["apps/web", "web"]);
  });
});

describe("origin badges", () => {
  test("a rule narrowed for a mounted pack and an override name what they change", () => {
    const files = [
      file(".agents/rules/core.md", "p-org"),
      file(".agents/rules/11-backend.md", "p-org", { narrowedBy: "s-kagi" }),
      file(".agents/rules/kagi/16-api.md", "s-kagi", {
        overridesPath: ".agents/rules/11-backend.md",
      }),
      file(".agents/rules/kagi/own.md", "s-kagi"),
    ];
    expect(Object.fromEntries(fileOrigins(files, OWNERS))).toEqual({
      ".agents/rules/11-backend.md": { kind: "NARROWED", label: "Narrowed for kagi" },
      ".agents/rules/kagi/16-api.md": {
        kind: "OVERRIDES",
        label: "Overrides .agents/rules/11-backend.md",
        target: ".agents/rules/11-backend.md",
      },
    });
  });

  test("a narrowing pack the list does not hold is named generally", () => {
    const origins = fileOrigins([file(".agents/rules/x.md", "p-org", { narrowedBy: "gone" })], OWNERS);
    expect(origins.get(".agents/rules/x.md")).toEqual({
      kind: "NARROWED",
      label: "Narrowed for a mounted pack",
    });
  });

  test("a file with neither marker has no origin badge", () => {
    expect(fileOrigins([file("AGENTS.md", "p-org")], OWNERS).size).toBe(0);
    expect(fileOrigins([], OWNERS).size).toBe(0);
  });

  test("a skill's origin names its pack", () => {
    expect(skillOrigin({ originName: "kagi" })).toEqual({ text: "Pack kagi", variant: "info" });
    expect(skillOrigin({ originName: "org" }).text).toBe("Pack org");
  });

  test("a folder's AGENTS.md is listed with the other agents files", () => {
    const files = [file("AGENTS.md", "p-org"), file("projects/kagi/AGENTS.md", "s-kagi")];
    expect(filesForTab(files, "agents-md").map((entry) => entry.path)).toEqual([
      "AGENTS.md",
      "projects/kagi/AGENTS.md",
    ]);
  });
});

describe("mount paths", () => {
  test("the allow-list takes the AGENTS.md of a folder at most four levels deep", () => {
    expect(isAllowedPath("projects/kagi/AGENTS.md")).toBe(true);
    expect(isAllowedPath("a/b/c/d/AGENTS.md")).toBe(true);
    expect(isAllowedPath("a/b/c/d/e/AGENTS.md")).toBe(false);
    expect(isAllowedPath(".x/AGENTS.md")).toBe(false);
    expect(isAllowedPath("x/../AGENTS.md")).toBe(false);
    expect(isAllowedPath("projects/kagi/README.md")).toBe(false);
  });

  test("the namespace holds the pack's rules, skills, agents, docs and AGENTS.md only", () => {
    for (const path of [
      ".agents/rules/kagi/16-api.md",
      ".agents/skills/kagi-release/SKILL.md",
      ".claude/skills/kagi-release",
      ".claude/agents/kagi-reviewer.md",
      ".agents/roles/kagi/engineer.md",
      "docs/private/conventions/projects/kagi/backend/x.md",
      "AGENTS.md",
    ]) {
      expect(isInMountNamespace(path, "kagi")).toBe(true);
    }
    for (const path of [
      ".agents/rules/core.md",
      ".agents/rules/kazan/x.md",
      ".agents/rules/kagi-extra/x.md",
      ".agents/rules/kagi/x.txt",
      ".claude/settings.json",
      ".agents/hooks/pre-tool-use.sh",
      ".agents/pipelines/feature.yaml",
      ".agents/pipelines/kagi/feature.yaml",
      ".agents/roles/kagi/",
      ".claude/agents/kagi-x/y.md",
      ".claude/commands/kagi-ship.md",
      ".agents/skills/kagi-release",
      "projects/kagi/AGENTS.md",
      ".agents/AGENTS.md",
      ".agents/skills/release/SKILL.md",
      "docs/private/conventions/backend/x.md",
    ]) {
      expect(isInMountNamespace(path, "kagi")).toBe(false);
    }
  });

  test("a draft of a mounted pack names a path outside the namespace in the server's words", () => {
    const issues = checkFile(
      { path: ".agents/rules/core.md", mode: "FILE", content: "# Core\n" },
      null,
      KAGI
    );
    expect(issues).toEqual([
      { rule: "MOUNT_PATH_OUTSIDE_NAMESPACE", message: mountNamespaceMessage(KAGI) },
    ]);
    expect(mountNamespaceMessage(KAGI)).toBe(
      "The path is outside the namespace of pack kagi, which is mounted in a folder; its files go under .agents/rules/kagi/, .agents/skills/kagi-*, docs/private/conventions/projects/kagi/ or AGENTS.md"
    );
    expect(
      checkFile({ path: ".agents/rules/core.md", mode: "FILE", content: "# Core\n" }, null)
    ).toEqual([]);
    expect(
      checkFile(
        {
          path: ".agents/rules/kagi/api.md",
          mode: "FILE",
          content: "---\npaths: projects/kagi/**\n---\n# Api\n",
        },
        null,
        KAGI
      )
    ).toEqual([]);
  });

  test("a rule of a mounted pack without paths under its folder is refused before the save", () => {
    const rule = ".agents/rules/kagi/api.md";
    const message = `The rule ${rule} of pack kagi needs paths frontmatter whose every pattern lies under projects/kagi/`;
    const refused = (frontmatter: string) =>
      mountRuleRefusal(rule, `---\n${frontmatter}\n---\n# Api\n`, KAGI);
    expect(mountRuleRefusal(rule, "# Api\n", KAGI)).toBe(message);
    expect(refused('paths: "**/*.java"')).toBe(message);
    expect(refused('paths: "projects/kagi*/**"')).toBe(message);
    expect(refused('paths: "{projects/kagi,projects/kazan}/**"')).toBe(message);
    expect(refused("paths: projects/kagi/**")).toBeNull();
    expect(refused('paths:\n  - "./projects/kagi/src/**"')).toBeNull();
    expect(mountRuleRefusal("AGENTS.md", "# A\n", KAGI)).toBeNull();
    expect(mountRuleRefusal(".agents/rules/kazan/api.md", "# A\n", KAGI)).toBeNull();
    expect(
      checkFile({ path: rule, mode: "FILE", content: "# Api\n" }, null, KAGI).map(
        (issue) => issue.rule
      )
    ).toEqual(["MOUNT_RULE_SCOPE"]);
  });

  test("a pack mounted in two folders is checked against both", () => {
    const twice: MountRef = { name: "kagi", mountPaths: ["projects/kagi", "tools/kagi"] };
    expect(
      mountRuleRefusal(".agents/rules/kagi/a.md", "---\npaths: projects/kagi/**\n---\n", twice)
    ).toBe(
      "The rule .agents/rules/kagi/a.md of pack kagi needs paths frontmatter whose every pattern lies under tools/kagi/"
    );
  });

  test("a skill of a mounted pack is named with the pack's prefix", () => {
    expect(skillNameIssue("release", [], "kagi")).toBe(
      "A skill of pack kagi, which is mounted in a folder, is named kagi-<name>."
    );
    expect(skillNameIssue("kagi-", [], "kagi")).not.toBeNull();
    expect(skillNameIssue("kagi-release", [], "kagi")).toBeNull();
    expect(skillNameIssue("release", [])).toBeNull();
  });

  test("new files of a mounted pack start in its namespace, and hooks are not offered", () => {
    expect(mountTabStart("rules", KAGI)).toBe(".agents/rules/kagi/");
    expect(mountTabStart("agents-md", KAGI)).toBe(AGENTS_FILE);
    expect(mountTabStart("subagents", KAGI)).toBe(".claude/agents/kagi-");
    expect(mountTabStart("roles", KAGI)).toBe(".agents/roles/kagi/");
    expect(mountTabStart("hooks", KAGI)).toBeNull();
    expect(mountDocsFolder(KAGI)).toBe("docs/private/conventions/projects/kagi/");
    expect(mountNewFileFolder("docs/private/conventions/backend/", KAGI)).toBe(
      "docs/private/conventions/projects/kagi/"
    );
    expect(mountNewFileFolder("docs/private/conventions/projects/kagi/api/", KAGI)).toBe(
      "docs/private/conventions/projects/kagi/api/"
    );
    expect(mountNewFileFolder("docs/private/conventions/backend/", null)).toBe(
      "docs/private/conventions/backend/"
    );
  });

  test("patterns are classified against the mount folder, brace groups one by one", () => {
    const classes = (pattern: string) => classifyPattern(pattern, "projects/kagi");
    expect(classes("projects/kagi/backend/**")).toEqual([["projects/kagi/backend/**", "INSIDE"]]);
    expect(classes("projects/kazan/**")).toEqual([["projects/kazan/**", "OUTSIDE"]]);
    expect(classes("projects/*/backend/**")).toEqual([["projects/*/backend/**", "SPANNING"]]);
    expect(classes("**/*.java")).toEqual([["**/*.java", "SPANNING"]]);
    expect(classes("projects/k*/x")).toEqual([["projects/k*/x", "SPANNING"]]);
    expect(classes("projects/z*/x")).toEqual([["projects/z*/x", "OUTSIDE"]]);
    expect(classes("projects/{kagi,kazan}/x/**")).toEqual([
      ["projects/kagi/x/**", "INSIDE"],
      ["projects/kazan/x/**", "OUTSIDE"],
    ]);
    expect(expandBraces("{a,b}{c,d}")).toEqual(["ac", "ad", "bc", "bd"]);
  });

  test("a rule's paths are read in the inline, block and comma-separated forms", () => {
    expect(rulePatterns('---\npaths: ["a/**", "b/**"]\n---\n')).toEqual(["a/**", "b/**"]);
    expect(rulePatterns("---\npaths:\n  - a/**\n  - 'b/**'\n---\n")).toEqual(["a/**", "b/**"]);
    expect(rulePatterns('---\npaths: "a/**, {b,c}/**"\n---\n')).toEqual(["a/**", "{b,c}/**"]);
    expect(rulePatterns("---\ndescription: x\n---\n")).toBeNull();
    expect(rulePatterns("# No frontmatter\n")).toBeNull();
  });
});
