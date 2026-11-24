import { describe, expect, test } from "bun:test";
import {
  PAGE_SLUG_PATTERN,
  isLevelAtLeast,
  pageDetailSchema,
  pageListItemSchema,
  pageRecentItemSchema,
  pageTrashItemSchema,
} from "./index";

const ID = "5c2d1e0f-3a4b-4c5d-8e9f-0a1b2c3d4e5f";
const SEEDED_ID = "b0000000-0000-0000-0000-000000000001";

describe("page schemas", () => {
  test("reads a tree row with titles of some locales and no level on a child", () => {
    const row = pageListItemSchema.parse({
      id: ID,
      parentId: SEEDED_ID,
      rootId: SEEDED_ID,
      slug: "A.5.1",
      path: "controls/A.5.1",
      position: 0,
      compliance: true,
      titles: { EN: "Policies for information security" },
      level: null,
    });

    expect(row.titles.TR).toBeUndefined();
  });

  test("rejects a timestamp without a zone", () => {
    const detail = {
      id: ID,
      parentId: null,
      rootId: null,
      slug: "policies",
      path: "policies",
      compliance: false,
      version: 3,
      level: "MANAGE",
      translations: [],
      updatedAt: "2026-11-05T10:15:30",
    };

    expect(pageDetailSchema.safeParse(detail).success).toBe(false);
    expect(
      pageDetailSchema.safeParse({ ...detail, updatedAt: "2026-11-05T10:15:30Z" }).success
    ).toBe(true);
  });

  test("reads the trash and recent rows of the contract, a missing title and name included", () => {
    expect(
      pageTrashItemSchema.safeParse({
        id: ID,
        title: null,
        path: "policies/old",
        deletedAt: "2026-11-21T08:00:00Z",
        deletedByName: null,
        descendantCount: 2,
      }).success
    ).toBe(true);
    expect(
      pageRecentItemSchema.safeParse({
        id: ID,
        title: "Policy",
        path: "policies",
        rootSlug: "policies",
        updatedAt: "2026-11-21T08:00:00Z",
        updatedByName: "Jane Doe",
      }).success
    ).toBe(true);
  });

  test("checks slugs as the server does", () => {
    expect(PAGE_SLUG_PATTERN.test("A.5.1")).toBe(true);
    expect(PAGE_SLUG_PATTERN.test("05_KALKAN_DOCS")).toBe(true);
    expect(PAGE_SLUG_PATTERN.test(".hidden")).toBe(false);
    expect(PAGE_SLUG_PATTERN.test("a/b")).toBe(false);
  });

  test("orders the levels as the server does", () => {
    expect(isLevelAtLeast("MANAGE", "EDIT")).toBe(true);
    expect(isLevelAtLeast("EDIT", "EDIT")).toBe(true);
    expect(isLevelAtLeast("VIEW", "EDIT")).toBe(false);
    expect(isLevelAtLeast(null, "VIEW")).toBe(false);
  });
});
