import { describe, expect, test } from "bun:test";
import type { PageListItem } from "../schemas";
import {
  ancestorIds,
  childrenOf,
  contentLocaleOf,
  depthOf,
  findSectionRoot,
  pageIdFromPathname,
  pageTitle,
} from "./page-tree";

function page(overrides: Partial<PageListItem> & Pick<PageListItem, "id" | "slug">): PageListItem {
  return {
    parentId: null,
    rootId: null,
    path: overrides.slug,
    position: 0,
    compliance: false,
    titles: {},
    level: null,
    ...overrides,
  };
}

const POLICIES = page({ id: "r1", slug: "policies", titles: { TR: "Politika", EN: "Policy" } });
const GUIDES = page({ id: "r2", slug: "Guides", titles: { EN: "Guide" } });
const SECOND = page({ id: "c2", slug: "b", parentId: "r1", rootId: "r1", position: 1 });
const FIRST = page({ id: "c1", slug: "a", parentId: "r1", rootId: "r1", position: 0 });
const GRANDCHILD = page({ id: "g1", slug: "c", parentId: "c1", rootId: "r1" });
const TREE = [POLICIES, GUIDES, SECOND, FIRST, GRANDCHILD];

describe("page tree", () => {
  test("reads Turkish content for a Turkish interface and English for any other", () => {
    expect(contentLocaleOf("tr")).toBe("TR");
    expect(contentLocaleOf("en")).toBe("EN");
    expect(contentLocaleOf("nl")).toBe("EN");
  });

  test("titles a page in the fitting locale, else the other one, else by its slug", () => {
    expect(pageTitle(POLICIES, "tr")).toBe("Politika");
    expect(pageTitle(POLICIES, "nl")).toBe("Policy");
    expect(pageTitle(GUIDES, "tr")).toBe("Guide");
    expect(pageTitle(FIRST, "en")).toBe("a");
  });

  test("lists the direct children of a page in sibling order", () => {
    expect(childrenOf(TREE, "r1").map((child) => child.id)).toEqual(["c1", "c2"]);
    expect(childrenOf(TREE, "r2")).toEqual([]);
  });

  test("finds a section's root by its slug, without regard to case, and never a child", () => {
    expect(findSectionRoot(TREE, "policies")?.id).toBe("r1");
    expect(findSectionRoot(TREE, "guides")?.id).toBe("r2");
    expect(findSectionRoot(TREE, "a")).toBeNull();
    expect(findSectionRoot(TREE, "risks")).toBeNull();
  });

  test("lists the ancestors of a page, its parent first and its root last", () => {
    const deep = [
      page({ id: "r", slug: "r" }),
      page({ id: "a", slug: "a", parentId: "r", rootId: "r", path: "r/a" }),
      page({ id: "b", slug: "b", parentId: "a", rootId: "r", path: "r/a/b" }),
      page({ id: "c", slug: "c", parentId: "b", rootId: "r", path: "r/a/b/c" }),
    ];
    expect(ancestorIds(deep, "c")).toEqual(["b", "a", "r"]);
    expect(ancestorIds(deep, "a")).toEqual(["r"]);
  });

  test("leaves the page itself out of its ancestors", () => {
    expect(ancestorIds(TREE, "c1")).toEqual(["r1"]);
    expect(ancestorIds(TREE, "g1")).toEqual(["c1", "r1"]);
  });

  test("has no ancestors for a root page, no page or an id the tree does not hold", () => {
    expect(ancestorIds(TREE, "r1")).toEqual([]);
    expect(ancestorIds(TREE, null)).toEqual([]);
    expect(ancestorIds(TREE, "unknown")).toEqual([]);
  });

  test("stops walking a malformed tree whose parents form a cycle", () => {
    const cycle = [
      page({ id: "x", slug: "x", parentId: "y" }),
      page({ id: "y", slug: "y", parentId: "x" }),
    ];
    expect(ancestorIds(cycle, "x").length).toBeLessThanOrEqual(10);
  });

  test("counts a root page at depth 1 and a page below it one deeper", () => {
    expect(depthOf({ path: "policies" })).toBe(1);
    expect(depthOf({ path: "policies/a/c" })).toBe(3);
  });

  test("reads the page id from a page path and its sub screens", () => {
    expect(pageIdFromPathname("/pages/abc")).toBe("abc");
    expect(pageIdFromPathname("/pages/abc/edit")).toBe("abc");
    expect(pageIdFromPathname("/pages/abc/history")).toBe("abc");
  });

  test("gives no page id for the new-page screen, a section or the overview", () => {
    expect(pageIdFromPathname("/pages/new")).toBeNull();
    expect(pageIdFromPathname("/sections/policies")).toBeNull();
    expect(pageIdFromPathname("/")).toBeNull();
    expect(pageIdFromPathname("/pages")).toBeNull();
  });
});
