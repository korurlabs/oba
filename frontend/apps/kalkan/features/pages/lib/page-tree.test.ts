import { describe, expect, test } from "bun:test";
import type { PageListItem } from "../schemas";
import { childrenOf, contentLocaleOf, findSectionRoot, pageTitle } from "./page-tree";

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
});
