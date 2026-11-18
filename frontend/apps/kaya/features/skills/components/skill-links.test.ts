import { describe, expect, test } from "bun:test";

import type { ScopeFile } from "@/features/scope/components/scope-files";

import { legacySkillRedirect, skillDetailHref, skillsListHref } from "./skill-links";
import { hasFileChoice, selectedSkillFile, skillFileLabel } from "./skill-rows";

const KAIZEN = ".agents/skills/kaizen";
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
    expect(skillDetailHref(KAIZEN, CURRENT)).toBe(`/skills/.agents%2Fskills%2Fkaizen?${KEPT}`);
    expect(skillDetailHref(KAIZEN, new URLSearchParams())).toBe(
      "/skills/.agents%2Fskills%2Fkaizen"
    );
  });

  test("a skill's page may open at one of its files", () => {
    expect(skillDetailHref(KAIZEN, new URLSearchParams(), `${KAIZEN}/workflows/sprint.md`)).toBe(
      "/skills/.agents%2Fskills%2Fkaizen?file=.agents%2Fskills%2Fkaizen%2Fworkflows%2Fsprint.md"
    );
  });

  test("an older ?skill= link leads to the skill's page with every other parameter", () => {
    expect(
      legacySkillRedirect(new URLSearchParams(`scope=pack%3Ap-1&skill=${KAIZEN}&file=a.md`))
    ).toBe("/skills/.agents%2Fskills%2Fkaizen?scope=pack%3Ap-1&file=a.md");
    expect(legacySkillRedirect(new URLSearchParams(`skill=${KAIZEN}`))).toBe(
      "/skills/.agents%2Fskills%2Fkaizen"
    );
    expect(legacySkillRedirect(new URLSearchParams("scope=pack%3Ap-1"))).toBeNull();
    expect(legacySkillRedirect(new URLSearchParams("skill="))).toBeNull();
  });
});

describe("skill file choice", () => {
  const own = [file(`${KAIZEN}/SKILL.md`), file(`${KAIZEN}/workflows/sprint.md`)];

  test("the named file shows, else the first, else none", () => {
    expect(selectedSkillFile(own, `${KAIZEN}/workflows/sprint.md`)?.path).toBe(
      `${KAIZEN}/workflows/sprint.md`
    );
    expect(selectedSkillFile(own, null)?.path).toBe(`${KAIZEN}/SKILL.md`);
    expect(selectedSkillFile(own, `${KAIZEN}/gone.md`)?.path).toBe(`${KAIZEN}/SKILL.md`);
    expect(selectedSkillFile([], null)).toBeNull();
  });

  test("only a skill with more than one file offers a choice", () => {
    expect(hasFileChoice(own)).toBe(true);
    expect(hasFileChoice(own.slice(0, 1))).toBe(false);
    expect(hasFileChoice([])).toBe(false);
  });

  test("a file is named within the skill's folder", () => {
    expect(skillFileLabel(KAIZEN, `${KAIZEN}/workflows/sprint.md`)).toBe("workflows/sprint.md");
    expect(skillFileLabel(KAIZEN, ".agents/skills/kaizen-other/SKILL.md")).toBe(
      ".agents/skills/kaizen-other/SKILL.md"
    );
  });
});
