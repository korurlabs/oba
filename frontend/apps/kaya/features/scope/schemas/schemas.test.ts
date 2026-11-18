import { describe, expect, test } from "bun:test";

import { createApiResponseSchema } from "@/types/api";

import {
  MOUNT_RESERVED_ROOTS,
  PACK_LIMITS,
  effectiveItemResponseSchema,
  mountFolderSchema,
  packCreateRequestSchema,
  packResponseSchema,
  packUpdateRequestSchema,
  repositoryPackResponseSchema,
  repositoryPackUpdateRequestSchema,
} from ".";

const PACK = {
  id: "k-1",
  name: "org",
  parentPackId: null,
  currentVersionId: "v-1",
  required: false,
  mountPaths: [],
  createdAt: "2026-10-23T09:00:00Z",
  updatedAt: null,
};

describe("packs", () => {
  test("a pack may lack a current version until its first approval", () => {
    expect(packResponseSchema.safeParse(PACK).success).toBe(true);
    expect(packResponseSchema.safeParse({ ...PACK, currentVersionId: null }).success).toBe(true);
    expect(packResponseSchema.safeParse({ ...PACK, name: undefined }).success).toBe(false);
  });

  test("a root pack has no parent, a child pack names its parent", () => {
    expect(packResponseSchema.safeParse({ ...PACK, parentPackId: undefined }).success).toBe(true);
    expect(packResponseSchema.safeParse({ ...PACK, parentPackId: "k-0" }).success).toBe(true);
  });

  test("a pack's description is optional plain text", () => {
    expect(packResponseSchema.safeParse({ ...PACK, description: null }).success).toBe(true);
    expect(packResponseSchema.parse({ ...PACK, description: "Org rules" }).description).toBe(
      "Org rules"
    );
  });

  test("a pack states whether it is required and where it is mounted", () => {
    expect(packResponseSchema.safeParse({ ...PACK, required: undefined }).success).toBe(false);
    expect(packResponseSchema.safeParse({ ...PACK, mountPaths: undefined }).success).toBe(false);
    expect(
      packResponseSchema.safeParse({ ...PACK, required: true, mountPaths: ["apps/web", "kagi"] })
        .success
    ).toBe(true);
  });

  test("a repository's packs come with their mount folder and its newest release, null before the first", () => {
    const entry = { ...PACK, mountPaths: undefined };
    const order = {
      repositoryId: "r-1",
      packs: [entry, { ...entry, id: "k-2", rootPath: "apps/web" }],
      release: null,
    };
    expect(repositoryPackResponseSchema.safeParse(order).success).toBe(true);
    const parsed = repositoryPackResponseSchema.parse(order);
    expect(parsed.packs.map((pack) => pack.rootPath ?? null)).toEqual([null, "apps/web"]);
    expect(
      repositoryPackResponseSchema.safeParse({
        ...order,
        release: {
          id: "rel-1",
          repositoryId: "r-1",
          number: 3,
          causeVersionId: "v-1",
          manifestSha256: "b".repeat(64),
          keyId: "key-1",
          fileCount: 1,
          createdAt: "2026-10-23T09:00:00Z",
        },
      }).success
    ).toBe(true);
  });
});

describe("a new pack", () => {
  test("takes a lowercase name, and a parent for a child pack", () => {
    expect(packCreateRequestSchema.safeParse({ name: "org" }).success).toBe(true);
    expect(packCreateRequestSchema.safeParse({ name: "kagi-web2" }).success).toBe(true);
    expect(packCreateRequestSchema.safeParse({ name: "kagi", parentPackId: "k-1" }).data).toEqual({
      name: "kagi",
      parentPackId: "k-1",
    });
    expect(packCreateRequestSchema.safeParse({ name: " kagi " }).data).toEqual({ name: "kagi" });
  });

  test("the parent is optional but not blank", () => {
    expect(
      packCreateRequestSchema.safeParse({ name: "kagi", parentPackId: undefined }).success
    ).toBe(true);
    expect(packCreateRequestSchema.safeParse({ name: "kagi", parentPackId: "" }).success).toBe(
      false
    );
  });

  test("refuses an empty, upper-case, spaced or too long name", () => {
    for (const name of ["", "Kagi", "ka gi", "kagi_web", "kagi/web", "a".repeat(61)]) {
      expect(packCreateRequestSchema.safeParse({ name }).success).toBe(false);
    }
    expect(
      packCreateRequestSchema.safeParse({ name: "a".repeat(PACK_LIMITS.nameMax) }).success
    ).toBe(true);
    const refused = packCreateRequestSchema.safeParse({ name: "Kagi" });
    expect(refused.error?.issues[0]?.message).toBe(
      "A pack name is 1 to 60 lowercase letters, digits or hyphens."
    );
  });

  test("a description is trimmed, a blank one becomes null, at most 1000 characters", () => {
    expect(packCreateRequestSchema.parse({ name: "kagi", description: " Kagi rules " })).toEqual({
      name: "kagi",
      description: "Kagi rules",
    });
    expect(packCreateRequestSchema.parse({ name: "kagi", description: "  " }).description).toBe(
      null
    );
    expect(
      packCreateRequestSchema.safeParse({ name: "kagi", description: "a".repeat(1000) }).success
    ).toBe(true);
    expect(
      packCreateRequestSchema.safeParse({ name: "kagi", description: "a".repeat(1001) }).success
    ).toBe(false);
  });
});

describe("a pack update", () => {
  test("always carries the description, null when it has none", () => {
    expect(packUpdateRequestSchema.parse({ name: "kagi", description: "" })).toEqual({
      name: "kagi",
      description: null,
    });
    expect(packUpdateRequestSchema.parse({ name: "kagi", description: null })).toEqual({
      name: "kagi",
      description: null,
    });
    expect(packUpdateRequestSchema.safeParse({ name: "kagi" }).success).toBe(false);
  });

  test("keeps the name rule of creation", () => {
    expect(packUpdateRequestSchema.safeParse({ name: "Kagi", description: null }).success).toBe(
      false
    );
  });
});

describe("a mount folder", () => {
  test("is 1 to 4 repository-relative segments", () => {
    for (const folder of ["kagi", "apps/web", "a/b/c/d", "_x", "v1.2/api-docs", " apps/web "]) {
      expect(mountFolderSchema.safeParse(folder).success).toBe(true);
    }
    expect(mountFolderSchema.parse(" apps/web ")).toBe("apps/web");
    for (const folder of [
      "",
      "   ",
      "a/b/c/d/e",
      ".hidden",
      "-x",
      "a//b",
      "/abs",
      "a/",
      "a/.b",
      "a b",
      "a".repeat(PACK_LIMITS.rootMax + 1),
    ]) {
      expect(mountFolderSchema.safeParse(folder).success).toBe(false);
    }
  });

  test("cannot start in a folder of the delivered config", () => {
    expect([...MOUNT_RESERVED_ROOTS].sort()).toEqual([".agents", ".claude", "docs"]);
    for (const folder of ["docs", "docs/private", ".agents", ".claude/x"]) {
      expect(mountFolderSchema.safeParse(folder).success).toBe(false);
    }
    expect(
      mountFolderSchema.safeParse("docs").error?.issues.map((issue) => issue.message)
    ).toContain(
      "The folder cannot start with .agents, .claude or docs, the folders of the delivered config."
    );
    expect(mountFolderSchema.safeParse("apps/docs").success).toBe(true);
    expect(mountFolderSchema.safeParse("documents").success).toBe(true);
  });
});

describe("a repository's pack list", () => {
  test("is sent whole, each pack at the root or in a mount folder", () => {
    expect(repositoryPackUpdateRequestSchema.safeParse({ packs: [] }).success).toBe(true);
    expect(
      repositoryPackUpdateRequestSchema.safeParse({
        packs: [
          { packId: "k-1" },
          { packId: "k-2", rootPath: null },
          { packId: "k-3", rootPath: "apps/web" },
        ],
      }).success
    ).toBe(true);
  });

  test("refuses a missing list, a blank pack and a bad folder", () => {
    expect(repositoryPackUpdateRequestSchema.safeParse({}).success).toBe(false);
    expect(repositoryPackUpdateRequestSchema.safeParse({ packs: [{ packId: "" }] }).success).toBe(
      false
    );
    expect(
      repositoryPackUpdateRequestSchema.safeParse({ packs: [{ packId: "k-1", rootPath: "docs" }] })
        .success
    ).toBe(false);
  });

  test("holds at most 50 packs", () => {
    const packs = (count: number) =>
      Array.from({ length: count }, (_, index: number) => ({ packId: `k-${index}` }));
    expect(
      repositoryPackUpdateRequestSchema.safeParse({ packs: packs(PACK_LIMITS.repositoryPacksMax) })
        .success
    ).toBe(true);
    const refused = repositoryPackUpdateRequestSchema.safeParse({
      packs: packs(PACK_LIMITS.repositoryPacksMax + 1),
    });
    expect(refused.success).toBe(false);
    expect(refused.error?.issues[0]?.message).toBe("A repository uses at most 50 packs.");
  });
});

describe("an effective item", () => {
  const ITEM = {
    path: ".agents/rules/core.md",
    kind: "RULE",
    sourcePackId: "k-1",
    sourceVersionId: "v-1",
    own: false,
  };

  test("reads an inherited item with its source and no lock or override", () => {
    const parsed = effectiveItemResponseSchema.safeParse(ITEM);
    expect(parsed.success).toBe(true);
    expect(parsed.data?.sourcePackId).toBe("k-1");
  });

  test("a locked item names why: its pack is required or it is a guardrail", () => {
    for (const locked of ["REQUIRED", "GUARDRAIL"]) {
      expect(effectiveItemResponseSchema.safeParse({ ...ITEM, locked }).success).toBe(true);
    }
    expect(effectiveItemResponseSchema.safeParse({ ...ITEM, locked: "SOFT" }).success).toBe(false);
    expect(effectiveItemResponseSchema.safeParse({ ...ITEM, locked: null }).success).toBe(true);
  });

  test("an own item that overrides an ancestor's carries its base and standing", () => {
    const override = {
      basePackId: "k-0",
      baseVersionId: "v-0",
      baseItemVersion: 2,
      parentVersionId: "v-3",
      parentItemVersion: 3,
      status: "PARENT_UPDATED",
    };
    const parsed = effectiveItemResponseSchema.safeParse({ ...ITEM, own: true, override });
    expect(parsed.success).toBe(true);
    expect(parsed.data?.override?.status).toBe("PARENT_UPDATED");
    for (const status of ["CURRENT", "PARENT_REMOVED"]) {
      expect(
        effectiveItemResponseSchema.safeParse({
          ...ITEM,
          own: true,
          override: { basePackId: "k-0", status },
        }).success
      ).toBe(true);
    }
    expect(
      effectiveItemResponseSchema.safeParse({
        ...ITEM,
        override: { basePackId: "k-0", status: "STALE" },
      }).success
    ).toBe(false);
    expect(
      effectiveItemResponseSchema.safeParse({ ...ITEM, override: { status: "CURRENT" } }).success
    ).toBe(false);
  });

  test("refuses an item without a path, a source or a known kind", () => {
    expect(effectiveItemResponseSchema.safeParse({ ...ITEM, path: undefined }).success).toBe(false);
    expect(
      effectiveItemResponseSchema.safeParse({ ...ITEM, sourcePackId: undefined }).success
    ).toBe(false);
    expect(effectiveItemResponseSchema.safeParse({ ...ITEM, own: undefined }).success).toBe(false);
    expect(effectiveItemResponseSchema.safeParse({ ...ITEM, kind: "NOPE" }).success).toBe(false);
  });
});

describe("the envelope around a scope read", () => {
  const envelope = createApiResponseSchema(packResponseSchema.array());

  test("a read answers success with its data", () => {
    expect(envelope.safeParse({ success: true, data: [PACK] }).success).toBe(true);
  });

  test("a refusal carries its code and values and no data", () => {
    const parsed = envelope.safeParse({
      success: false,
      message: "The pack was not found.",
      error: { code: "KAY_NF_001", message: "Not found", errorValues: { id: "k-9" } },
    });
    expect(parsed.success).toBe(true);
    if (!parsed.success) throw new Error("expected the refusal to parse");
    expect(parsed.data.error?.code).toBe("KAY_NF_001");
    expect(parsed.data.data ?? null).toBeNull();
  });

  test("data that does not match the schema fails the envelope", () => {
    expect(envelope.safeParse({ success: true, data: [{ name: "x" }] }).success).toBe(false);
    expect(envelope.safeParse({ success: false, error: {} }).success).toBe(false);
  });
});
