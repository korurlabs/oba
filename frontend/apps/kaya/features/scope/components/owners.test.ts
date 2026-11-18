import { describe, expect, test } from "bun:test";

import type { Owner, PackResponse, RepositoryPackResponse } from "../schemas";
import {
  chainOf,
  descendantIds,
  flattenPackTree,
  ownerById,
  ownersOf,
  packTree,
  type PackNode,
} from "./owners";

function owner(id: string, name: string, parentPackId: string | null = null): Owner {
  return {
    id,
    name,
    parentPackId,
    currentVersionId: null,
    required: false,
    mountPaths: [],
    repositoryIds: [],
  };
}

function pack(overrides: Partial<PackResponse> & Pick<PackResponse, "id" | "name">): PackResponse {
  return {
    required: false,
    mountPaths: [],
    createdAt: "2026-10-23T09:00:00Z",
    ...overrides,
  };
}

function list(repositoryId: string, packIds: string[]): RepositoryPackResponse {
  return {
    repositoryId,
    packs: packIds.map((id: string) => ({
      id,
      name: id,
      required: false,
      createdAt: "2026-10-23T09:00:00Z",
    })),
    release: null,
  };
}

/** The tree as nested names, for a readable comparison. */
function names(nodes: readonly PackNode[]): unknown[] {
  return nodes.map((node: PackNode) =>
    node.children.length === 0 ? node.owner.name : [node.owner.name, names(node.children)]
  );
}

describe("ownersOf", () => {
  test("each pack becomes an owner with the repositories whose list names it", () => {
    const owners = ownersOf(
      [
        pack({ id: "k-org", name: "org", required: true }),
        pack({
          id: "k-web",
          name: "web",
          parentPackId: "k-org",
          mountPaths: ["apps/web"],
          currentVersionId: "v-4",
        }),
      ],
      [list("r-1", ["k-org", "k-web"]), list("r-2", ["k-org"])]
    );
    expect(owners).toEqual([
      {
        id: "k-org",
        name: "org",
        parentPackId: null,
        currentVersionId: null,
        required: true,
        mountPaths: [],
        repositoryIds: ["r-1", "r-2"],
      },
      {
        id: "k-web",
        name: "web",
        parentPackId: "k-org",
        currentVersionId: "v-4",
        required: false,
        mountPaths: ["apps/web"],
        repositoryIds: ["r-1"],
      },
    ]);
  });

  test("a pack no repository uses has no repositories, and no packs give no owners", () => {
    expect(ownersOf([pack({ id: "k-1", name: "a" })], [])[0]?.repositoryIds).toEqual([]);
    expect(ownersOf([], [list("r-1", ["k-1"])])).toEqual([]);
  });
});

describe("ownerById", () => {
  test("finds a pack by id and answers undefined for an unknown one", () => {
    const owners = [owner("a", "a"), owner("b", "b")];
    expect(ownerById(owners, "b")?.name).toBe("b");
    expect(ownerById(owners, "x")).toBeUndefined();
  });
});

describe("chainOf", () => {
  const owners = [owner("root", "root"), owner("mid", "mid", "root"), owner("leaf", "leaf", "mid")];

  test("a pack and its ancestors, root first", () => {
    expect(chainOf(owners, "leaf").map((entry: Owner) => entry.id)).toEqual(["root", "mid", "leaf"]);
    expect(chainOf(owners, "mid").map((entry: Owner) => entry.id)).toEqual(["root", "mid"]);
    expect(chainOf(owners, "root").map((entry: Owner) => entry.id)).toEqual(["root"]);
  });

  test("an unknown pack has no chain", () => {
    expect(chainOf(owners, "gone")).toEqual([]);
  });

  test("the chain stops at a parent the list does not hold", () => {
    const orphaned = [owner("a", "a", "missing"), owner("b", "b", "a")];
    expect(chainOf(orphaned, "b").map((entry: Owner) => entry.id)).toEqual(["a", "b"]);
  });

  test("a parent loop ends and lists each pack once", () => {
    const loop = [owner("a", "a", "b"), owner("b", "b", "a")];
    const chain = chainOf(loop, "a").map((entry: Owner) => entry.id);
    expect(chain.sort()).toEqual(["a", "b"]);
    const self = [owner("s", "s", "s")];
    expect(chainOf(self, "s").map((entry: Owner) => entry.id)).toEqual(["s"]);
  });
});

describe("descendantIds", () => {
  const owners = [
    owner("root", "root"),
    owner("a", "a", "root"),
    owner("b", "b", "root"),
    owner("a1", "a1", "a"),
    owner("a11", "a11", "a1"),
    owner("other", "other"),
  ];

  test("every pack below a pack, nearest level first, without the pack itself", () => {
    expect(descendantIds(owners, "root")).toEqual(["a", "b", "a1", "a11"]);
    expect(descendantIds(owners, "a")).toEqual(["a1", "a11"]);
  });

  test("a leaf and an unknown pack have none", () => {
    expect(descendantIds(owners, "a11")).toEqual([]);
    expect(descendantIds(owners, "gone")).toEqual([]);
  });

  test("a parent loop ends and never lists the pack itself", () => {
    const loop = [owner("a", "a", "b"), owner("b", "b", "a")];
    expect(descendantIds(loop, "a")).toEqual(["b"]);
  });
});

describe("packTree", () => {
  test("root packs come by name with their children nested, each level by name", () => {
    const tree = packTree([
      owner("z", "zeta"),
      owner("c2", "web", "k"),
      owner("k", "kagi"),
      owner("c1", "api", "k"),
      owner("g", "deep", "c1"),
      owner("a", "alpha"),
    ]);
    expect(names(tree)).toEqual(["alpha", ["kagi", [["api", ["deep"]], "web"]], "zeta"]);
  });

  test("a pack whose parent the list does not hold is shown as a root", () => {
    const tree = packTree([owner("a", "alpha"), owner("o", "orphan", "missing")]);
    expect(names(tree)).toEqual(["alpha", "orphan"]);
  });

  test("a parent loop does not hang and lists each pack once", () => {
    const tree = packTree([owner("a", "a", "b"), owner("b", "b", "a"), owner("c", "c")]);
    const flat = flattenPackTree(tree).map((entry) => entry.owner.id);
    expect([...flat].sort()).toEqual(["a", "b", "c"]);
    expect(flat).toHaveLength(3);
  });

  test("a pack that is its own parent is listed once", () => {
    const flat = flattenPackTree(packTree([owner("s", "s", "s")]));
    expect(flat.map((entry) => entry.owner.id)).toEqual(["s"]);
  });

  test("no packs give an empty tree", () => {
    expect(packTree([])).toEqual([]);
  });
});

describe("flattenPackTree", () => {
  test("reads the tree top down with each pack's depth", () => {
    const flat = flattenPackTree(
      packTree([
        owner("k", "kagi"),
        owner("c1", "api", "k"),
        owner("g", "deep", "c1"),
        owner("c2", "web", "k"),
        owner("a", "alpha"),
      ])
    );
    expect(flat.map((entry) => [entry.owner.name, entry.depth])).toEqual([
      ["alpha", 0],
      ["kagi", 0],
      ["api", 1],
      ["deep", 2],
      ["web", 1],
    ]);
  });

  test("starts at the given depth and is empty for no nodes", () => {
    const nodes = packTree([owner("a", "a"), owner("b", "b", "a")]);
    expect(flattenPackTree(nodes, 2).map((entry) => entry.depth)).toEqual([2, 3]);
    expect(flattenPackTree([])).toEqual([]);
  });
});
