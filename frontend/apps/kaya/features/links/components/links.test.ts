import { describe, expect, test } from "bun:test";

import type { ScopeFile } from "@/features/scope/components/scope-files";
import { fileOfTarget, linkHref } from "./links";

function file(path: string): ScopeFile {
  return {
    path,
    mode: "FILE",
    sha256: null,
    size: null,
    target: null,
    alwaysOn: null,
    versionId: "v-1",
    ownerId: "s-1",
  };
}

const RULE = ".agents/rules/11-backend-rules.md";
const DOC = "docs/private/conventions/backend/32-soft-delete-convention.md";

const FILES: ScopeFile[] = [
  file(".agents/rules/09-skill-routing.md"),
  file(RULE),
  file(".agents/skills/kazan/SKILL.md"),
  file(".claude/settings.json"),
  file("AGENTS.md"),
  file("docs/private/conventions/backend/03-service-cqrs-convention.md"),
  file(DOC),
];

const SEARCH = new URLSearchParams("scope=repository%3Ar-1&draft=d-1&tab=rules&file=x");

describe("the page a target opens", () => {
  test("a file is itself; a directory opens its first file", () => {
    expect(fileOfTarget(DOC, FILES)).toBe(DOC);
    expect(fileOfTarget("docs/private/conventions/backend/", FILES)).toBe(
      "docs/private/conventions/backend/03-service-cqrs-convention.md"
    );
    expect(fileOfTarget("docs/private/conventions/frontend/", FILES)).toBeNull();
  });

  test("a doc opens on Conventions, keeping the scope, dropping a draft parameter and not the Rules tab", () => {
    expect(linkHref(DOC, FILES, SEARCH)).toBe(
      `/conventions?scope=repository%3Ar-1&file=${encodeURIComponent(DOC)}`
    );
  });

  test("a rule and AGENTS.md open on their Rules detail page", () => {
    expect(linkHref(RULE, FILES, new URLSearchParams())).toBe(
      `/rules/rules/${encodeURIComponent(RULE)}`
    );
    expect(linkHref("AGENTS.md", FILES, new URLSearchParams("release=4"))).toBe(
      "/rules/agents-md/AGENTS.md?release=4"
    );
  });

  test("a subagent opens on its Rules detail page", () => {
    const agent = ".claude/agents/coder.md";
    expect(linkHref(agent, [...FILES, file(agent)], SEARCH)).toBe(
      `/rules/subagents/${encodeURIComponent(agent)}?scope=repository%3Ar-1`
    );
  });

  test("a Rules detail link keeps the inherited setting; other pages do not", () => {
    const search = new URLSearchParams("scope=pack%3Ap-1&inherited=true");
    expect(linkHref(RULE, FILES, search)).toBe(
      `/rules/rules/${encodeURIComponent(RULE)}?scope=pack%3Ap-1&inherited=true`
    );
    expect(linkHref(DOC, FILES, search)).not.toContain("inherited");
  });

  test("a skill directory or file opens the skill; settings open the Allowlist", () => {
    expect(linkHref(".agents/skills/kazan", FILES, new URLSearchParams())).toBe(
      "/skills?skill=.agents%2Fskills%2Fkazan"
    );
    expect(linkHref(".agents/skills/kazan/SKILL.md", FILES, new URLSearchParams())).toBe(
      "/skills?skill=.agents%2Fskills%2Fkazan"
    );
    expect(linkHref(".claude/settings.json", FILES, new URLSearchParams())).toBe("/allowlist");
  });

  test("a part of a skill opens the skill's document at that file", () => {
    const files: ScopeFile[] = [...FILES, file(".agents/skills/kazan/workflows/sprint.md")];
    expect(
      linkHref(".agents/skills/kazan/workflows/sprint.md", files, new URLSearchParams())
    ).toBe(
      "/skills?skill=.agents%2Fskills%2Fkazan&file=.agents%2Fskills%2Fkazan%2Fworkflows%2Fsprint.md"
    );
  });

  test("a .claude/skills symlink opens the skill it points at, the key the Skills page lists", () => {
    const linked: ScopeFile[] = [
      ...FILES,
      { ...file(".claude/skills/kazan"), mode: "SYMLINK", target: "../../.agents/skills/kazan" },
      {
        ...file(".claude/skills/review/SKILL.md"),
        mode: "SYMLINK",
        target: "../../../.agents/skills/kazan/SKILL.md",
      },
      { ...file(".claude/skills/dangling"), mode: "SYMLINK", target: "../../.agents/skills/gone" },
    ];
    const kazan = "/skills?skill=.agents%2Fskills%2Fkazan";
    expect(linkHref(".claude/skills/kazan", linked, new URLSearchParams())).toBe(kazan);
    expect(linkHref(".claude/skills/kazan/SKILL.md", linked, new URLSearchParams())).toBe(kazan);
    expect(linkHref(".claude/skills/review/", linked, new URLSearchParams())).toBe(kazan);
    expect(linkHref(".claude/skills/dangling", linked, new URLSearchParams())).toBeNull();
  });

  test("a .claude/skills plugin with its own SKILL.md opens under its own directory", () => {
    const plugin: ScopeFile[] = [...FILES, file(".claude/skills/lint/SKILL.md")];
    expect(linkHref(".claude/skills/lint/SKILL.md", plugin, SEARCH)).toBe(
      "/skills?scope=repository%3Ar-1&skill=.claude%2Fskills%2Flint"
    );
  });

  test("a skill directory without a regular SKILL.md opens nothing", () => {
    const noSkill: ScopeFile[] = [...FILES, file(".agents/skills/notes/README.md")];
    expect(linkHref(".agents/skills/notes", noSkill, SEARCH)).toBeNull();
  });

  test("a target the scope does not deliver opens nothing", () => {
    expect(linkHref(".agents/rules/01-response-style.md", FILES, SEARCH)).toBeNull();
    expect(linkHref(".agents/skills/missing", FILES, SEARCH)).toBeNull();
  });
});
