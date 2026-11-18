import { describe, expect, test } from "bun:test";

import { releaseDetailResponseSchema, releaseQueryRequestSchema, releaseResponseSchema } from ".";

const RELEASE = {
  id: "rel-1",
  repositoryId: "r-1",
  number: 3,
  causeVersionId: "v-1",
  manifestSha256: "b".repeat(64),
  keyId: "key-1",
  fileCount: 1,
  createdAt: "2026-10-23T09:00:00Z",
};

describe("repository releases", () => {
  test("reads a release summary and a detail with its entries", () => {
    expect(releaseResponseSchema.safeParse(RELEASE).success).toBe(true);
    const detail = {
      ...RELEASE,
      signature: "sig",
      files: [
        {
          path: "AGENTS.md",
          sha256: "a".repeat(64),
          mode: "FILE",
          target: null,
          size: 4,
          alwaysOn: true,
          ownerId: "p-1",
          versionId: "v-1",
        },
        {
          path: ".claude/skills/x",
          mode: "SYMLINK",
          target: "../../.agents/skills/x",
          alwaysOn: false,
          ownerId: "k-1",
          versionId: "v-2",
          overridesPath: null,
          narrowedBy: null,
        },
      ],
      packVersions: [],
    };
    expect(releaseDetailResponseSchema.safeParse(detail).success).toBe(true);
  });

  test("a detail lists the pack versions it composed, at the root or in a mount folder", () => {
    const detail = {
      ...RELEASE,
      signature: "sig",
      files: [],
      packVersions: [
        { packId: "k-org", versionId: "v-1", rootPath: null },
        { packId: "k-web", versionId: "v-4", rootPath: "apps/web" },
        { packId: "k-new" },
        { packId: "k-empty", versionId: null, rootPath: null },
      ],
    };
    const parsed = releaseDetailResponseSchema.safeParse(detail);
    expect(parsed.success).toBe(true);
    expect(parsed.data?.packVersions.map((entry) => entry.packId)).toEqual([
      "k-org",
      "k-web",
      "k-new",
      "k-empty",
    ]);
  });

  test("a detail without its pack versions, or with a pack version lacking its pack, is refused", () => {
    const detail = { ...RELEASE, signature: "sig", files: [] };
    expect(releaseDetailResponseSchema.safeParse(detail).success).toBe(false);
    expect(
      releaseDetailResponseSchema.safeParse({ ...detail, packVersions: [{ versionId: "v-1" }] })
        .success
    ).toBe(false);
  });

  test("a detail needs a signature and an always-on flag per entry", () => {
    expect(releaseDetailResponseSchema.safeParse({ ...RELEASE, files: [] }).success).toBe(false);
    const entry = { path: "AGENTS.md", mode: "FILE", ownerId: "p", versionId: "v" };
    expect(
      releaseDetailResponseSchema.safeParse({ ...RELEASE, signature: "s", files: [entry], packVersions: [] }).success
    ).toBe(false);
  });

  test("the release query is 1-based with 1 to 50 per page", () => {
    expect(releaseQueryRequestSchema.safeParse({}).success).toBe(true);
    expect(releaseQueryRequestSchema.safeParse({ page: 1, amount: 50 }).success).toBe(true);
    expect(releaseQueryRequestSchema.safeParse({ page: 0 }).success).toBe(false);
    expect(releaseQueryRequestSchema.safeParse({ amount: 51 }).success).toBe(false);
    expect(releaseQueryRequestSchema.safeParse({ amount: 0 }).success).toBe(false);
    expect(releaseQueryRequestSchema.safeParse({ page: 1.5 }).success).toBe(false);
  });
});
