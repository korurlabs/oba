import { describe, expect, test } from "bun:test";
import type { PageListItem } from "@/features/pages/schemas";
import {
  ancestorsOf,
  buildNodes,
  descendantCountOf,
  dropTargetOf,
  moveTargetOf,
  planMove,
  POSITION_GAP,
} from "./tree-moves";

function page(id: string, parentId: string | null, position: number): PageListItem {
  return {
    id,
    parentId,
    rootId: parentId === null ? null : "root",
    slug: id,
    path: id,
    position,
    compliance: false,
    titles: {},
    level: parentId === null ? "EDIT" : null,
  };
}

// root
// ├── a (0)
// │   └── a1 (0)
// ├── b (1)
// └── c (2)
const ROOT = page("root", null, 0);
const A = page("a", "root", 0);
const A1 = page("a1", "a", 0);
const B = page("b", "root", 1);
const C = page("c", "root", 2);
const TREE = [ROOT, C, A, B, A1];

const byId = (id: string) => TREE.find((candidate) => candidate.id === id)!;

describe("tree shape", () => {
  test("nests the pages below a parent, each level in sibling order", () => {
    const nodes = buildNodes(TREE, "root");
    expect(nodes.map((node) => node.page.id)).toEqual(["a", "b", "c"]);
    expect(nodes[0]!.children.map((node) => node.page.id)).toEqual(["a1"]);
  });

  test("lists the ancestors from the root down and counts the pages below", () => {
    expect(ancestorsOf(TREE, "a1").map((ancestor) => ancestor.id)).toEqual(["root", "a"]);
    expect(ancestorsOf(TREE, "root")).toEqual([]);
    expect(descendantCountOf(TREE, "root")).toBe(4);
    expect(descendantCountOf(TREE, "a")).toBe(1);
    expect(descendantCountOf(TREE, "c")).toBe(0);
  });
});

describe("move targets", () => {
  test("moves a page up and down among its siblings, never past the ends", () => {
    expect(moveTargetOf(TREE, byId("b"), "up", "root")).toEqual({ parentId: "root", index: 0 });
    expect(moveTargetOf(TREE, byId("b"), "down", "root")).toEqual({ parentId: "root", index: 2 });
    expect(moveTargetOf(TREE, byId("a"), "up", "root")).toBeNull();
    expect(moveTargetOf(TREE, byId("c"), "down", "root")).toBeNull();
  });

  test("indents a page under the sibling above it, after that sibling's children", () => {
    expect(moveTargetOf(TREE, byId("b"), "indent", "root")).toEqual({ parentId: "a", index: 1 });
    expect(moveTargetOf(TREE, byId("a"), "indent", "root")).toBeNull();
  });

  test("outdents a page right after its parent, but never out of the section", () => {
    expect(moveTargetOf(TREE, byId("a1"), "outdent", "root")).toEqual({
      parentId: "root",
      index: 1,
    });
    expect(moveTargetOf(TREE, byId("b"), "outdent", "root")).toBeNull();
    expect(moveTargetOf(TREE, ROOT, "up", "root")).toBeNull();
  });

  test("drops a page just before the page it lands on, never into its own subtree", () => {
    expect(dropTargetOf(TREE, "c", byId("a"))).toEqual({ parentId: "root", index: 0 });
    expect(dropTargetOf(TREE, "c", byId("a1"))).toEqual({ parentId: "a", index: 0 });
    expect(dropTargetOf(TREE, "a", byId("a1"))).toBeNull();
    expect(dropTargetOf(TREE, "a", byId("a"))).toBeNull();
    expect(dropTargetOf(TREE, "a", byId("b"))).toBeNull();
    expect(dropTargetOf(TREE, "a", ROOT)).toBeNull();
  });
});

describe("move plan", () => {
  test("uses one request when a free sort key lies between the neighbours", () => {
    const spaced = [ROOT, page("x", "root", 0), page("y", "root", 1024), page("z", "root", 2048)];
    expect(planMove(spaced, "z", { parentId: "root", index: 1 })).toEqual([
      { pageId: "z", parentId: "root", position: 512 },
    ]);
  });

  test("appends after the last sibling and starts an empty parent at zero", () => {
    expect(planMove(TREE, "a", { parentId: "root", index: 2 })).toEqual([
      { pageId: "a", parentId: "root", position: 2 + POSITION_GAP },
    ]);
    expect(planMove(TREE, "b", { parentId: "c", index: 0 })).toEqual([
      { pageId: "b", parentId: "c", position: 0 },
    ]);
  });

  test("spreads the siblings out first when the neighbours leave no free key", () => {
    // c goes before b, and nothing fits between a (0) and b (1). a already sits on 0, so
    // only b moves aside before c is placed.
    expect(planMove(TREE, "c", { parentId: "root", index: 1 })).toEqual([
      { pageId: "b", parentId: "root", position: 2 * POSITION_GAP },
      { pageId: "c", parentId: "root", position: POSITION_GAP },
    ]);
  });

  test("treats siblings on the same key as having no room between them", () => {
    const tied = [ROOT, page("x", "root", 5), page("y", "root", 5), page("z", "root", 9)];
    const steps = planMove(tied, "z", { parentId: "root", index: 1 });
    expect(steps.at(-1)).toEqual({ pageId: "z", parentId: "root", position: POSITION_GAP });
    expect(steps).toContainEqual({ pageId: "x", parentId: "root", position: 0 });
    expect(steps).toContainEqual({ pageId: "y", parentId: "root", position: 2 * POSITION_GAP });
  });
});
