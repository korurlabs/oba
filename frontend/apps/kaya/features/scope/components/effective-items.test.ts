import { describe, expect, test } from "bun:test";

import type { VersionDetailResponse } from "@/features/versions/schemas";

import type { EffectiveItemResponse, EffectiveOverrideResponse, Owner } from "../schemas";
import {
  effectiveByItem,
  effectiveOf,
  inheritedFiles,
  itemOf,
  lockRefusal,
  lockText,
  overrideStatusText,
  overridesLabel,
  withInherited,
} from "./effective-items";
import type { ScopeFile } from "./scope-files";

const OWNERS = [
  { id: "p-org", name: "org" },
  { id: "p-village", name: "village" },
] as Owner[];

function item(overrides: Partial<EffectiveItemResponse> & Pick<EffectiveItemResponse, "path">) {
  return {
    kind: "RULE",
    sourcePackId: "p-org",
    sourceVersionId: "v-org",
    own: false,
    locked: null,
    override: null,
    ...overrides,
  } as EffectiveItemResponse;
}

function file(path: string, ownerId = "p-village", versionId = "v-village"): ScopeFile {
  return {
    path,
    mode: "FILE",
    sha256: null,
    size: null,
    target: null,
    alwaysOn: null,
    versionId,
    ownerId,
  };
}

const OVERRIDE: EffectiveOverrideResponse = {
  basePackId: "p-org",
  baseVersionId: "v-4",
  baseItemVersion: 4,
  parentVersionId: "v-5",
  parentItemVersion: 5,
  status: "PARENT_UPDATED",
};

describe("items", () => {
  test("every file of a skill belongs to its directory; any other file is its own item", () => {
    expect(itemOf(".agents/skills/kaizen/SKILL.md")).toBe(".agents/skills/kaizen/");
    expect(itemOf(".agents/skills/kaizen/scripts/run.sh")).toBe(".agents/skills/kaizen/");
    expect(itemOf(".agents/rules/core.md")).toBe(".agents/rules/core.md");
    expect(itemOf(".claude/skills/kaizen")).toBe(".claude/skills/kaizen");
  });

  test("a file finds the effective item of its skill", () => {
    const skill = item({ path: ".agents/skills/kaizen/", kind: "SKILL" });
    const effective = effectiveByItem([skill]);
    expect(effectiveOf(effective, ".agents/skills/kaizen/SKILL.md")).toBe(skill);
    expect(effectiveOf(effective, ".agents/rules/core.md")).toBeUndefined();
  });
});

describe("inherited files", () => {
  const org = {
    id: "v-org",
    packId: "p-org",
    files: [
      { path: ".agents/rules/core.md", mode: "FILE" },
      { path: ".agents/skills/kaizen/SKILL.md", mode: "FILE" },
      { path: ".agents/skills/kaizen/run.sh", mode: "EXECUTABLE" },
      { path: "AGENTS.md", mode: "FILE" },
    ],
  } as VersionDetailResponse;

  test("only the inherited items' files are read from the version that gives them", () => {
    const items = [
      item({ path: ".agents/rules/core.md" }),
      item({ path: ".agents/skills/kaizen/", kind: "SKILL" }),
      item({ path: "AGENTS.md", own: true, sourcePackId: "p-village", sourceVersionId: "v-v" }),
    ];
    expect(inheritedFiles(items, [org]).map((entry: ScopeFile) => entry.path)).toEqual([
      ".agents/rules/core.md",
      ".agents/skills/kaizen/SKILL.md",
      ".agents/skills/kaizen/run.sh",
    ]);
    expect(inheritedFiles(items, [])).toEqual([]);
  });

  test("an item the pack holds keeps only its own files, in path order", () => {
    const own = [file("AGENTS.md"), file(".agents/skills/kaizen/SKILL.md")];
    const inherited = [
      file(".agents/rules/core.md", "p-org", "v-org"),
      file(".agents/skills/kaizen/run.sh", "p-org", "v-org"),
    ];
    expect(withInherited(own, inherited).map((entry: ScopeFile) => entry.path)).toEqual([
      ".agents/rules/core.md",
      ".agents/skills/kaizen/SKILL.md",
      "AGENTS.md",
    ]);
  });
});

describe("locks", () => {
  const effective = effectiveByItem([
    item({ path: ".agents/rules/guardrails/no-push.md", locked: "GUARDRAIL" }),
    item({ path: ".agents/rules/core.md", locked: "REQUIRED" }),
    item({ path: ".agents/rules/free.md" }),
  ]);

  test("a locked item names the pack that locks it", () => {
    expect(lockText(effective.get(".agents/rules/core.md")!, OWNERS)).toBe("Required by org");
    expect(lockText(effective.get(".agents/rules/guardrails/no-push.md")!, OWNERS)).toBe(
      "Guardrail, set by org"
    );
    expect(lockText(effective.get(".agents/rules/free.md")!, OWNERS)).toBeNull();
  });

  test("a save at a locked path is refused in the server's words", () => {
    expect(lockRefusal(".agents/rules/core.md", effective, OWNERS)).toBe(
      "The item .agents/rules/core.md is locked: pack org is required, so no descendant can override it"
    );
    expect(lockRefusal(" .agents/rules/guardrails/no-push.md", effective, OWNERS)).toBe(
      "The item .agents/rules/guardrails/no-push.md is locked: it is a guardrail of pack org, so no descendant can override it"
    );
    expect(lockRefusal(".agents/rules/free.md", effective, OWNERS)).toBeNull();
    expect(lockRefusal(".agents/rules/new.md", effective, OWNERS)).toBeNull();
  });
});

describe("override status", () => {
  test("a parent update names both item versions", () => {
    expect(overridesLabel(OVERRIDE, OWNERS)).toBe("Overrides org");
    expect(overrideStatusText(OVERRIDE, OWNERS)).toBe("org updated to v5, you override v4.");
  });

  test("an unknown base, a removed parent and a current override", () => {
    expect(
      overrideStatusText({ ...OVERRIDE, baseVersionId: null, baseItemVersion: null }, OWNERS)
    ).toBe("org is at v5; the version your override is based on is not known.");
    expect(overrideStatusText({ ...OVERRIDE, status: "PARENT_REMOVED" }, OWNERS)).toBe(
      "org no longer gives this item. Your pack keeps it as its own item until you decide."
    );
    expect(overrideStatusText({ ...OVERRIDE, status: "CURRENT" }, OWNERS)).toBeNull();
  });
});
