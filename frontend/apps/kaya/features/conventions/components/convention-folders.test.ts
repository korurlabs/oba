import { describe, expect, test } from "bun:test";

import type { ScopeFile } from "@/features/scope/components/scope-files";

import { conventionGroups } from "./convention-files";
import {
  folderKey,
  folderRows,
  openingFolder,
  parseOpenFolders,
  sameOpenFolders,
  serializeOpenFolders,
  toggledFolder,
  visibleRows,
  type VisibleRow,
} from "./convention-folders";

const ROOT = "docs/private/conventions/";

function doc(path: string): ScopeFile {
  return {
    path: `${ROOT}${path}`,
    mode: "FILE",
    sha256: null,
    size: null,
    target: null,
    alwaysOn: null,
    versionId: "v-1",
    ownerId: "p-1",
  };
}

const DOCS = [
  doc("_index.md"),
  doc("backend/01-entities.md"),
  doc("backend/api/01-controllers.md"),
  doc("backend/api/02-errors.md"),
  doc("backend-legacy/01-old.md"),
  doc("security/deep/01-secrets.md"),
];
const ROWS = folderRows(conventionGroups(DOCS));

function names(rows: readonly VisibleRow[]): string[] {
  return rows.map((entry: VisibleRow) =>
    entry.kind === "folder"
      ? `${entry.row.key}${entry.open ? " open" : ""}`
      : entry.file.path.slice(ROOT.length)
  );
}

describe("open folders in the URL", () => {
  test("a folder is keyed by its path below the docs root, the root by a dot", () => {
    expect(folderKey(ROOT)).toBe(".");
    expect(folderKey(`${ROOT}backend/`)).toBe("backend");
    expect(folderKey(`${ROOT}backend/api/`)).toBe("backend/api");
  });

  test("parsing drops blanks, slashes at the ends and repeats, and sorts", () => {
    expect(parseOpenFolders(null)).toEqual([]);
    expect(parseOpenFolders("")).toEqual([]);
    expect(parseOpenFolders("security, backend/,,backend")).toEqual(["backend", "security"]);
  });

  test("serialising sorts, so equal sets give equal values", () => {
    expect(serializeOpenFolders(["security", "backend", "security"])).toBe("backend,security");
    expect(serializeOpenFolders([])).toBe("");
    expect(sameOpenFolders(["a", "b"], ["b", "a"])).toBe(true);
    expect(sameOpenFolders(["a"], ["a", "b"])).toBe(false);
  });

  test("toggling opens a closed folder and closes an open one", () => {
    expect(toggledFolder(["security"], "backend")).toEqual(["backend", "security"]);
    expect(toggledFolder(["backend", "security"], "backend")).toEqual(["security"]);
  });

  test("opening a nested folder opens the folders above it too", () => {
    expect(openingFolder(["security"], `${ROOT}backend/api/`)).toEqual([
      "backend",
      "backend/api",
      "security",
    ]);
    expect(openingFolder([], ROOT)).toEqual(["."]);
  });
});

describe("folderRows", () => {
  test("one row per folder in tree order, with a row for a folder that holds only folders", () => {
    expect(ROWS.map((row) => row.key)).toEqual([
      ".",
      "backend",
      "backend/api",
      "backend-legacy",
      "security",
      "security/deep",
    ]);
    expect(ROWS.map((row) => row.label)).toEqual([
      ROOT,
      "backend/",
      "api/",
      "backend-legacy/",
      "security/",
      "deep/",
    ]);
    expect(ROWS.map((row) => row.depth)).toEqual([0, 0, 1, 0, 0, 1]);
  });

  test("a folder counts its own and nested docs; the root only its own", () => {
    expect(ROWS.map((row) => row.count)).toEqual([1, 3, 2, 1, 1, 1]);
    expect(ROWS.find((row) => row.key === "security")?.docs).toEqual([]);
  });
});

describe("visibleRows", () => {
  test("with every folder closed only the top folders show", () => {
    expect(names(visibleRows(ROWS, []))).toEqual([".", "backend", "backend-legacy", "security"]);
  });

  test("an open folder shows its docs, then its subfolders", () => {
    expect(names(visibleRows(ROWS, ["backend"]))).toEqual([
      ".",
      "backend open",
      "backend/01-entities.md",
      "backend/api",
      "backend-legacy",
      "security",
    ]);
  });

  test("a nested folder stays hidden while the folder above it is closed", () => {
    expect(names(visibleRows(ROWS, ["backend/api"]))).toEqual([
      ".",
      "backend",
      "backend-legacy",
      "security",
    ]);
    expect(names(visibleRows(ROWS, [".", "backend", "backend/api"]))).toEqual([
      ". open",
      "_index.md",
      "backend open",
      "backend/01-entities.md",
      "backend/api open",
      "backend/api/01-controllers.md",
      "backend/api/02-errors.md",
      "backend-legacy",
      "security",
    ]);
  });
});
