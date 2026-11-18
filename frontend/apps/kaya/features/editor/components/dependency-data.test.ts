import { describe, expect, test } from "bun:test";

import type { Dependency, DependencyResponse } from "@/features/links/schemas";

import {
  isSkillPart,
  LINKS_TO_LABELS,
  namesText,
  partGate,
  sharedPartWarning,
  sharingSkills,
  USED_BY_LABELS,
} from "./dependency-data";

const PART = ".agents/skills/kaizen/workflows/sprint.md";

function used(path: string, relation: Dependency["relation"] = "SKILL_PART"): Dependency {
  return { path, kind: "SKILL", relation, status: "FOUND", versionId: "v-1" };
}

function response(usedBy: Dependency[]): DependencyResponse {
  return { path: PART, kind: "WORKFLOW", linksTo: [], usedBy };
}

describe("shared parts", () => {
  test("two or more skills made of the part share it, named in order", () => {
    const shared = response([
      used(".agents/skills/kaizen/SKILL.md"),
      used(".agents/skills/kagi/SKILL.md"),
      used("docs/a.md", "MARKDOWN_LINK"),
    ]);
    expect(sharingSkills(shared)).toEqual(["kagi", "kaizen"]);
  });

  test("a part of one skill only is not shared", () => {
    expect(sharingSkills(response([used(".agents/skills/kaizen/SKILL.md")]))).toBeNull();
    expect(sharingSkills(undefined)).toBeNull();
  });

  test("skill names read as a sentence", () => {
    expect(namesText(["kagi"])).toBe("kagi");
    expect(namesText(["kagi", "kaizen"])).toBe("kagi and kaizen");
    expect(namesText(["a", "b", "c"])).toBe("a, b and c");
  });

  test("only a file of a skill directory other than SKILL.md is a part", () => {
    expect(isSkillPart(PART)).toBe(true);
    expect(isSkillPart(".claude/skills/kaizen/scripts/run.sh")).toBe(true);
    expect(isSkillPart(".agents/skills/kaizen/SKILL.md")).toBe(false);
    expect(isSkillPart(".agents/rules/a.md")).toBe(false);
  });
});

describe("partGate", () => {
  const shared = response([
    used(".agents/skills/kaizen/SKILL.md"),
    used(".agents/skills/kagi/SKILL.md"),
  ]);

  test("a file that is no part opens at once, without waiting for its links", () => {
    expect(partGate({ path: "AGENTS.md", dependencies: undefined, failed: false })).toEqual({
      kind: "OPEN",
    });
  });

  test("a shared part asks first with the skills' names", () => {
    expect(partGate({ path: PART, dependencies: shared, failed: false })).toEqual({
      kind: "ASK",
      skills: ["kagi", "kaizen"],
    });
  });

  test("a part waits for its sharers, and asks without names when they cannot be read", () => {
    expect(partGate({ path: PART, dependencies: undefined, failed: false }).kind).toBe("LOADING");
    expect(partGate({ path: PART, dependencies: undefined, failed: true })).toEqual({
      kind: "ASK",
      skills: null,
    });
  });

  test("a part of one skill opens at once", () => {
    const own = response([used(".agents/skills/kaizen/SKILL.md")]);
    expect(partGate({ path: PART, dependencies: own, failed: false }).kind).toBe("OPEN");
  });
});

describe("sharedPartWarning", () => {
  test("a file that is no shared part saves without a question", () => {
    expect(sharedPartWarning({ kind: "OPEN" })).toBeNull();
  });

  test("a save of a shared part asks with the skills' names", () => {
    expect(sharedPartWarning({ kind: "ASK", skills: ["kagi", "kaizen"] })).toBe(
      "The skills kagi and kaizen share this part. A save changes all of them."
    );
  });

  test("a save asks without names while the sharers load or when they cannot be read", () => {
    expect(sharedPartWarning({ kind: "LOADING" })).toContain("still reading");
    expect(sharedPartWarning({ kind: "ASK", skills: null })).toContain("could not read");
  });
});

describe("relation labels", () => {
  test("every relation has a label on both sides", () => {
    for (const relation of [
      "MARKDOWN_LINK",
      "RULE_CONVENTION",
      "SKILL_PART",
    ] as const) {
      expect(LINKS_TO_LABELS[relation]).toBeTruthy();
      expect(USED_BY_LABELS[relation]).toBeTruthy();
    }
  });
});
