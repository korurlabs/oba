import { describe, expect, test } from "bun:test";

import type { ScopeFile } from "@/features/scope/components/scope-files";
import type { Owner } from "@/features/scope/schemas";

import {
  fileListLabel,
  filterSkillFileGroups,
  groupSkillFiles,
  skillPaths,
  fileSkillRows,
  filterSkillRows,
  firstSentence,
  serverSkillRows,
  type SkillFileRead,
  type SkillRow,
} from "./skill-rows";

const PACK: Owner = {
  id: "k-org",
  name: "org",
  parentPackId: null,
  currentVersionId: "v-1",
  required: false,
  mountPaths: [],
  repositoryIds: ["r-1"],
};

const ROOT: Owner = {
  id: "p-1",
  name: "village",
  parentPackId: "k-org",
  currentVersionId: "v-2",
  required: false,
  mountPaths: [],
  repositoryIds: ["r-1"],
};

const OWNERS = [PACK, ROOT];
const ownerOf = (id: string) => OWNERS.find((owner: Owner) => owner.id === id);

function file(path: string, ownerId = "k-org", versionId = "v-1"): ScopeFile {
  return {
    path,
    mode: "FILE",
    sha256: null,
    size: 1,
    target: null,
    alwaysOn: null,
    versionId,
    ownerId,
  };
}

describe("skill rows", () => {
  test("server rows take the name and description of the version's skill list, by name", () => {
    const files = [
      file(".agents/skills/plan/SKILL.md", "p-1", "v-2"),
      file(".agents/skills/kazan/SKILL.md"),
      file(".agents/skills/kazan/references/cli.md"),
    ];
    const rows = serverSkillRows(files, ownerOf, [
      {
        id: "sk-1",
        versionId: "v-1",
        path: "kazan",
        name: "kazan",
        description: "Ticket work.",
        sizeBytes: 10,
      },
    ]);
    expect(
      rows.map((row: SkillRow) => [row.name, row.description, row.originName, row.fileCount])
    ).toEqual([
      ["kazan", "Ticket work.", "org", 2],
      ["plan", null, "village", 1],
    ]);
  });

  test("file rows read their name and description from SKILL.md once it loads", () => {
    const files = [
      file(".agents/skills/b-dir/SKILL.md"),
      file(".agents/skills/a-dir/SKILL.md"),
      file(".agents/skills/a-dir/notes.md"),
    ];
    const reads: Record<string, SkillFileRead> = {
      ".agents/skills/b-dir/SKILL.md": {
        content: "---\nname: zeta\ndescription: Does zeta.\npaths: [src/**]\n---\n",
      },
      ".agents/skills/a-dir/SKILL.md": null,
    };
    const rows = fileSkillRows(
      files,
      ownerOf,
      (skillFile: ScopeFile) => reads[skillFile.path] ?? null
    );
    expect(
      rows.map((row: SkillRow) => [row.name, row.description, row.fileCount, row.paths])
    ).toEqual([
      ["a-dir", undefined, 2, null],
      ["zeta", "Does zeta.", 1, ["src/**"]],
    ]);
  });

  test("a failed read shows its error and no description", () => {
    const rows = fileSkillRows([file(".agents/skills/x/SKILL.md")], ownerOf, () => ({
      error: "boom",
    }));
    expect(rows[0]).toMatchObject({ name: "x", description: null, error: "boom" });
  });

  test("search needs every word in name, description or origin", () => {
    const rows = serverSkillRows(
      [file(".agents/skills/kazan/SKILL.md"), file(".agents/skills/plan/SKILL.md", "p-1", "v-2")],
      ownerOf,
      [
        {
          id: "sk-1",
          versionId: "v-1",
          path: "kazan",
          name: "kazan",
          description: "Ticket work",
          sizeBytes: 10,
        },
      ]
    );
    expect(filterSkillRows(rows, "ticket ORG").map((row: SkillRow) => row.name)).toEqual([
      "kazan",
    ]);
    expect(filterSkillRows(rows, "")).toHaveLength(2);
  });

  test("the first sentence ends at its full stop", () => {
    expect(firstSentence("Use it for X. Then Y.")).toBe("Use it for X.");
    expect(firstSentence("No stop here")).toBe("No stop here");
  });

  test("a server row without paths takes them from its SKILL.md once read", () => {
    const content = "---\nname: kazan\npaths:\n  - projects/kazan/**\n---\nBody";
    expect(skillPaths({ paths: null }, content)).toEqual(["projects/kazan/**"]);
    expect(skillPaths({ paths: null }, undefined)).toBeNull();
    expect(skillPaths({ paths: null }, "---\nname: kazan\n---\n")).toBeNull();
    expect(skillPaths({ paths: ["a/**"] }, content)).toEqual(["a/**"]);
  });
});

describe("skill file list", () => {
  const SHADCN = ".agents/skills/shadcn";
  const own = [
    "SKILL.md",
    "agents/openai.yml",
    "assets/shadcn-small.png",
    "assets/shadcn.png",
    "cli.md",
    "customization.md",
    "evals/evals.json",
    "mcp.md",
    "rules/base-vs-radix.md",
    "rules/composition.md",
    "rules/forms.md",
    "rules/icons.md",
    "rules/styling.md",
  ].map((name: string) => file(`${SHADCN}/${name}`));
  const at = (name: string): ScopeFile => file(`${SHADCN}/${name}`);

  test("groups the root first with SKILL.md leading, then folders alphabetically", () => {
    const shuffled = [
      at("rules/forms.md"),
      at("cli.md"),
      at("agents/openai.yml"),
      at("SKILL.md"),
      at("evals/evals.json"),
      at("assets/shadcn-small.png"),
    ];
    expect(
      groupSkillFiles(SHADCN, shuffled).map((group) => [
        group.folder,
        group.files.map((entry) => entry.name),
      ])
    ).toEqual([
      [null, ["SKILL.md", "cli.md"]],
      ["agents", ["openai.yml"]],
      ["assets", ["shadcn-small.png"]],
      ["evals", ["evals.json"]],
      ["rules", ["forms.md"]],
    ]);
  });

  test("keeps each file's full path and names nested folders by their whole path", () => {
    const groups = groupSkillFiles(SHADCN, [at("SKILL.md"), at("rules/deep/a.md")]);
    expect(groups[1]).toEqual({
      folder: "rules/deep",
      files: [{ path: `${SHADCN}/rules/deep/a.md`, name: "a.md" }],
    });
  });

  test("search keeps files whose path within the skill holds every word, in any case", () => {
    const groups = groupSkillFiles(SHADCN, own);
    expect(
      filterSkillFileGroups(groups, "RULES form").map((group) => [
        group.folder,
        group.files.map((entry) => entry.name),
      ])
    ).toEqual([["rules", ["forms.md"]]]);
    expect(
      filterSkillFileGroups(groups, "shadcn").flatMap((group) => group.files.map((e) => e.name))
    ).toEqual(["shadcn-small.png", "shadcn.png"]);
    expect(filterSkillFileGroups(groups, "nothing-here")).toEqual([]);
    expect(filterSkillFileGroups(groups, "  ")).toEqual(groups);
  });

  test("the list button counts the files on SKILL.md and names any other open file", () => {
    expect(fileListLabel(SHADCN, own, at("SKILL.md"))).toBe("Files (13)");
    expect(fileListLabel(SHADCN, own, null)).toBe("Files (13)");
    expect(fileListLabel(SHADCN, own, at("rules/forms.md"))).toBe("rules/forms.md");
  });
});
