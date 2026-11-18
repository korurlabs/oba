import { describe, expect, test } from "bun:test";

import type { ScopeFile } from "@/features/scope/components/scope-files";

import {
  conventionFiles,
  conventionGroups,
  conventionTitles,
  conventionVersionIds,
  folderOf,
  newFileFolder,
  type ConventionGroup,
} from "./convention-files";

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

/** Each group as its label followed by its docs' file names. */
function outline(groups: readonly ConventionGroup[]): string[] {
  return groups.flatMap((group: ConventionGroup) => [
    group.label,
    ...group.docs.map((doc: ScopeFile) => `  ${doc.path.slice(group.folder.length)}`),
  ]);
}

const FILES = [
  file("docs/private/conventions/frontend/01-a.md"),
  file(".agents/rules/11-backend-rules.md"),
  file("docs/private/conventions/backend/32-soft-delete-convention.md"),
  file("docs/private/conventions/_index.md"),
  file("docs/private/conventions/backend/03-service-cqrs-convention.md"),
  file("docs/private/conventions/backend/sub/x.md"),
  file("docs/private/other.md"),
];

describe("the convention groups", () => {
  test("only the files under the docs prefix, in path order", () => {
    expect(conventionFiles(FILES).map((entry: ScopeFile) => entry.path)).toEqual([
      "docs/private/conventions/_index.md",
      "docs/private/conventions/backend/03-service-cqrs-convention.md",
      "docs/private/conventions/backend/32-soft-delete-convention.md",
      "docs/private/conventions/backend/sub/x.md",
      "docs/private/conventions/frontend/01-a.md",
    ]);
  });

  test("each folder is a group of its own docs, a nested folder included, in path order", () => {
    expect(outline(conventionGroups(FILES))).toEqual([
      "docs/private/conventions/",
      "  _index.md",
      "backend/",
      "  03-service-cqrs-convention.md",
      "  32-soft-delete-convention.md",
      "backend/sub/",
      "  x.md",
      "frontend/",
      "  01-a.md",
    ]);
  });

  test("a group carries its folder's repository path", () => {
    expect(conventionGroups(FILES)[1]?.folder).toBe("docs/private/conventions/backend/");
  });

  test("a folder's docs stay together when a nested folder sorts between them", () => {
    const groups = conventionGroups([
      file("docs/private/conventions/backend/a.md"),
      file("docs/private/conventions/backend/sub/x.md"),
      file("docs/private/conventions/backend/z.md"),
    ]);
    expect(outline(groups)).toEqual(["backend/", "  a.md", "  z.md", "backend/sub/", "  x.md"]);
  });

  test("no docs give no groups", () => {
    expect(conventionGroups([file(".agents/rules/a.md")])).toEqual([]);
  });
});

describe("where a new doc starts", () => {
  test("in the open doc's folder, else at the docs root", () => {
    expect(folderOf("docs/private/conventions/backend/32-soft-delete-convention.md")).toBe(
      "docs/private/conventions/backend/"
    );
    expect(folderOf("docs/private/conventions/_index.md")).toBe("docs/private/conventions/");
    expect(folderOf(null)).toBe("docs/private/conventions/");
    expect(folderOf(".agents/rules/a.md")).toBe("docs/private/conventions/");
  });
});

describe("where New file starts", () => {
  const OPEN = "docs/private/conventions/backend/32-soft-delete-convention.md";

  test("in the selected folder, a nested one included", () => {
    expect(newFileFolder("docs/private/conventions/frontend/", OPEN, FILES)).toBe(
      "docs/private/conventions/frontend/"
    );
    expect(newFileFolder("docs/private/conventions/backend/sub/", null, FILES)).toBe(
      "docs/private/conventions/backend/sub/"
    );
  });

  test("with no folder selected, in the open doc's folder as before", () => {
    expect(newFileFolder(null, OPEN, FILES)).toBe("docs/private/conventions/backend/");
    expect(newFileFolder(null, null, FILES)).toBe("docs/private/conventions/");
  });

  test("a selected folder the scope no longer delivers falls back to the open doc", () => {
    expect(newFileFolder("docs/private/conventions/removed/", OPEN, FILES)).toBe(
      "docs/private/conventions/backend/"
    );
  });
});

describe("convention titles", () => {
  const docs = [
    file("docs/private/conventions/backend/a.md"),
    { ...file("docs/private/conventions/general/b.md"), versionId: "v-2" },
  ];

  test("a delivered doc takes the title of its version's row", () => {
    const titles = conventionTitles(docs, [
      { id: "c-1", versionId: "v-1", path: "backend/a.md", title: "Logging", sizeBytes: 1 },
      { id: "c-2", versionId: "v-2", path: "general/b.md", title: null, sizeBytes: 1 },
      // A pack's doc that a mounted pack replaces: same path, another version.
      { id: "c-3", versionId: "v-9", path: "general/b.md", title: "Replaced", sizeBytes: 1 },
      // A convention only a skill holds has no path in the tree.
      { id: "c-4", versionId: "v-1", path: null, title: "Skill only", sizeBytes: 1 },
    ]);
    expect([...titles]).toEqual([["docs/private/conventions/backend/a.md", "Logging"]]);
  });

  test("the docs need their versions once each", () => {
    expect(conventionVersionIds([...docs, file("docs/private/conventions/c.md")])).toEqual([
      "v-1",
      "v-2",
    ]);
  });
});
