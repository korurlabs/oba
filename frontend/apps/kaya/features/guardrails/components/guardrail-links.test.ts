import { describe, expect, test } from "bun:test";

import type { ScopeFile } from "@/features/scope/components/scope-files";

import {
  GUARDRAILS_SECTION,
  guardrailDetailHref,
  guardrailNewHref,
  guardrailSavedHref,
  guardrailsListHref,
} from "./guardrail-links";

const CURRENT = new URLSearchParams("scope=pack%3Ap-1&inherited=true&panel=links&tab=hooks");
const KEPT = "scope=pack%3Ap-1&inherited=true";
const NO_PUSH = ".agents/rules/guardrails/no-push.md";

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

describe("guardrail links", () => {
  test("the list, detail and new pages keep only the scope, release, draft and inherited setting", () => {
    expect(guardrailsListHref(CURRENT)).toBe(`/guardrails?${KEPT}`);
    expect(guardrailDetailHref(NO_PUSH, CURRENT)).toBe(
      `/guardrails/.agents%2Frules%2Fguardrails%2Fno-push.md?${KEPT}`
    );
    expect(guardrailNewHref(new URLSearchParams())).toBe("/guardrails/new");
  });

  test("a save that keeps the file a guardrail stays on Guardrails, one that moves it goes to Rules", () => {
    expect(guardrailSavedHref(NO_PUSH, CURRENT)).toBe(guardrailDetailHref(NO_PUSH, CURRENT));
    expect(guardrailSavedHref(".agents/rules/no-push.md", CURRENT)).toBe(
      `/rules/rules/.agents%2Frules%2Fno-push.md?${KEPT}`
    );
  });

  test("the section lists only guardrails, links back to Guardrails and adds none in a mounted pack", () => {
    const files = [file(".agents/rules/00-core.md"), file(NO_PUSH)];
    expect(GUARDRAILS_SECTION.files(files).map((entry: ScopeFile) => entry.path)).toEqual([
      NO_PUSH,
    ]);
    expect(GUARDRAILS_SECTION.back).toBe("Back to Guardrails");
    expect(GUARDRAILS_SECTION.listHref(CURRENT)).toBe(`/guardrails?${KEPT}`);
    expect(GUARDRAILS_SECTION.newStart(null)).toEqual({
      path: ".agents/rules/guardrails/",
      executable: false,
    });
    expect(GUARDRAILS_SECTION.newStart({ name: "kagi", mountPaths: ["projects/kagi"] })).toBeNull();
  });
});
