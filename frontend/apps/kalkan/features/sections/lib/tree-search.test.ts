import { describe, expect, test } from "bun:test";
import type { PageListItem } from "@/features/pages/schemas";
import {
  collapsedOf,
  NO_FOLDS,
  pageMatchesSearch,
  searchKeyOf,
  syncFoldsToSearch,
  toggleFold,
  visiblePageIds,
} from "./tree-search";

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

describe("tree folds", () => {
  const search = (text: string) => searchKeyOf(visiblePageIds(TREE, ROOT.id, text, "en"));

  test("opens a page the member folded while a search shows a hit below it", () => {
    const folded = toggleFold(NO_FOLDS, ACCESS.id);
    expect(collapsedOf(folded).has(ACCESS.id)).toBe(true);

    const searching = syncFoldsToSearch(folded, search("password"));
    expect(collapsedOf(searching).size).toBe(0);
  });

  test("brings back the member's own folds once the search is cleared", () => {
    const folded = toggleFold(NO_FOLDS, ACCESS.id);
    const searching = toggleFold(syncFoldsToSearch(folded, search("password")), BACKUP.id);
    expect([...collapsedOf(searching)]).toEqual([BACKUP.id]);

    const cleared = syncFoldsToSearch(searching, searchKeyOf(undefined));
    expect([...collapsedOf(cleared)]).toEqual([ACCESS.id]);
  });

  test("opens every page again when the search keeps other pages", () => {
    const searching = toggleFold(syncFoldsToSearch(NO_FOLDS, search("password")), ACCESS.id);
    expect(syncFoldsToSearch(searching, search("password"))).toBe(searching);

    const changed = syncFoldsToSearch(searching, search("access"));
    expect(search("access")).not.toBe(search("password"));
    expect(collapsedOf(changed).size).toBe(0);
  });
});
