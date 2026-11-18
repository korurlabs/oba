import { describe, expect, test } from "bun:test";

import type { ScopeFile } from "@/features/scope/components/scope-files";

import { legacySkillRedirect, skillDetailHref, skillsListHref } from "./skill-links";
import { hasFileChoice, selectedSkillFile, skillFileLabel } from "./skill-rows";

const KAZAN = ".agents/skills/kazan";
const CURRENT = new URLSearchParams("scope=pack%3Ap-1&inherited=true&at=v-0&panel=links");
const KEPT = "scope=pack%3Ap-1&inherited=true";

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

describe("skill links", () => {
  test("the list and a skill's page keep only the scope, release, draft and inherited setting", () => {
    expect(skillsListHref(CURRENT)).toBe(`/skills?${KEPT}`);
    expect(skillDetailHref(KAZAN, CURRENT)).toBe(`/skills/.agents%2Fskills%2Fkazan?${KEPT}`);
    expect(skillDetailHref(KAZAN, new URLSearchParams())).toBe(
      "/skills/.agents%2Fskills%2Fkazan"
    );
  });

  test("a skill's page may open at one of its files", () => {
    expect(skillDetailHref(KAZAN, new URLSearchParams(), `${KAZAN}/workflows/sprint.md`)).toBe(
      "/skills/.agents%2Fskills%2Fkazan?file=.agents%2Fskills%2Fkazan%2Fworkflows%2Fsprint.md"
    );
  });

  test("an older ?skill= link leads to the skill's page with every other parameter", () => {
    expect(
      legacySkillRedirect(new URLSearchParams(`scope=pack%3Ap-1&skill=${KAZAN}&file=a.md`))
    ).toBe("/skills/.agents%2Fskills%2Fkazan?scope=pack%3Ap-1&file=a.md");
    expect(legacySkillRedirect(new URLSearchParams(`skill=${KAZAN}`))).toBe(
      "/skills/.agents%2Fskills%2Fkazan"
    );
    expect(legacySkillRedirect(new URLSearchParams("scope=pack%3Ap-1"))).toBeNull();
    expect(legacySkillRedirect(new URLSearchParams("skill="))).toBeNull();
  });
});

describe("skill file choice", () => {
  const own = [file(`${KAZAN}/SKILL.md`), file(`${KAZAN}/workflows/sprint.md`)];

  test("the named file shows, else the first, else none", () => {
    expect(selectedSkillFile(own, `${KAZAN}/workflows/sprint.md`)?.path).toBe(
      `${KAZAN}/workflows/sprint.md`
    );
    expect(selectedSkillFile(own, null)?.path).toBe(`${KAZAN}/SKILL.md`);
    expect(selectedSkillFile(own, `${KAZAN}/gone.md`)?.path).toBe(`${KAZAN}/SKILL.md`);
    expect(selectedSkillFile([], null)).toBeNull();
  });

  test("only a skill with more than one file offers a choice", () => {
    expect(hasFileChoice(own)).toBe(true);
    expect(hasFileChoice(own.slice(0, 1))).toBe(false);
    expect(hasFileChoice([])).toBe(false);
  });

  test("a file is named within the skill's folder", () => {
    expect(skillFileLabel(KAZAN, `${KAZAN}/workflows/sprint.md`)).toBe("workflows/sprint.md");
    expect(skillFileLabel(KAZAN, ".agents/skills/kazan-other/SKILL.md")).toBe(
      ".agents/skills/kazan-other/SKILL.md"
    );
  });
});
