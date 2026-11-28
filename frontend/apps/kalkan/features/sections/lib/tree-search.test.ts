import { describe, expect, test } from "bun:test";
import type { PageListItem } from "@/features/pages/schemas";
import { pageMatchesSearch, visiblePageIds } from "./tree-search";

function page(
  id: string,
  parentId: string | null,
  position: number,
  titles: PageListItem["titles"] = {}
): PageListItem {
  return {
    id,
    parentId,
    rootId: parentId === null ? null : "root",
    slug: id,
    path: id,
    position,
    compliance: false,
    titles,
    level: parentId === null ? "EDIT" : null,
  };
}

// root
// ├── access (Access control)
// │   └── passwords (Password rules / Parola kuralları)
// └── backup (Backups)
const ROOT = page("root", null, 0, { EN: "Policies" });
const ACCESS = page("access", "root", 0, { EN: "Access control" });
const PASSWORDS = page("passwords", "access", 0, { EN: "Password rules", TR: "Parola kuralları" });
const BACKUP = page("backup", "root", 1, { EN: "Backups" });
const TREE = [ROOT, ACCESS, PASSWORDS, BACKUP];

describe("pageMatchesSearch", () => {
  test("matches the reader's title without regard to case", () => {
    expect(pageMatchesSearch(PASSWORDS, "PASSWORD", "en")).toBe(true);
    expect(pageMatchesSearch(PASSWORDS, "parola", "tr")).toBe(true);
    expect(pageMatchesSearch(PASSWORDS, "parola", "en")).toBe(false);
  });

  test("matches the address too", () => {
    expect(pageMatchesSearch(BACKUP, "back", "en")).toBe(true);
  });
});

describe("visiblePageIds", () => {
  test("keeps a hit with every page above it, the root left out", () => {
    expect([...visiblePageIds(TREE, "root", "password", "en")].sort()).toEqual([
      "access",
      "passwords",
    ]);
  });

  test("keeps a hit without the pages below it that do not answer", () => {
    expect([...visiblePageIds(TREE, "root", "access", "en")]).toEqual(["access"]);
  });

  test("is empty when no page answers", () => {
    expect(visiblePageIds(TREE, "root", "risk", "en").size).toBe(0);
  });
});
