import { describe, expect, test } from "bun:test";

import type { ScopeFile } from "@/features/scope/components/scope-files";

import {
  GUARDRAILS_FOLDER,
  guardrailFiles,
  isGuardrailPath,
  newGuardrailStart,
} from "./guardrail-files";

function file(path: string): ScopeFile {
  return {
    path,
    mode: "FILE",
    sha256: null,
    size: null,
    target: null,
    alwaysOn: null,
    versionId: "v-1",
    ownerId: "p-1",
  };
}

describe("guardrail files", () => {
  test("only rules under the guardrails folder are guardrails", () => {
    expect(isGuardrailPath(".agents/rules/guardrails/no-push.md")).toBe(true);
    expect(isGuardrailPath(".agents/rules/guardrails/git/no-force.md")).toBe(true);
    expect(isGuardrailPath(GUARDRAILS_FOLDER)).toBe(false);
    expect(isGuardrailPath(".agents/rules/00-core.md")).toBe(false);
    expect(isGuardrailPath(".agents/rules/guardrails.md")).toBe(false);
    expect(isGuardrailPath(".agents/rules/kagi/guardrails/no-push.md")).toBe(false);
    expect(isGuardrailPath("docs/.agents/rules/guardrails/no-push.md")).toBe(false);
  });

  test("the list keeps the guardrails in the scope's order and drops every other file", () => {
    const files = [
      file(".agents/rules/00-core.md"),
      file(".agents/rules/guardrails/a.md"),
      file(".agents/skills/x/SKILL.md"),
      file(".agents/rules/guardrails/b.md"),
    ];
    expect(guardrailFiles(files).map((entry) => entry.path)).toEqual([
      ".agents/rules/guardrails/a.md",
      ".agents/rules/guardrails/b.md",
    ]);
  });

  test("a new guardrail starts in the guardrails folder, and a mounted pack adds none", () => {
    expect(newGuardrailStart(null)).toBe(GUARDRAILS_FOLDER);
    expect(newGuardrailStart({ name: "kagi", mountPaths: ["projects/kagi"] })).toBeNull();
  });
});
