import { describe, expect, test } from "bun:test";
import type { PageListItem } from "@/features/pages/schemas";
import { SECTION_SLUGS } from "@/lib/sections";
import { sectionCounts } from "./section-counts";

function page(id: string, slug: string, parentId: string | null, rootId: string | null) {
  return {
    id,
    parentId,
    rootId,
    slug,
    path: slug,
    position: 0,
    compliance: false,
    titles: {},
    level: parentId === null ? "VIEW" : null,
  } satisfies PageListItem;
}

describe("sectionCounts", () => {
  test("counts every page below a section root, at any depth, without the root", () => {
    const pages = [
      page("r1", "policies", null, null),
      page("a", "access", "r1", "r1"),
      page("b", "backup", "r1", "r1"),
      page("c", "keys", "a", "r1"),
      page("r2", "Controls", null, null),
    ];

    const counts = sectionCounts(pages);

    expect(counts.policies).toEqual({ pageCount: 3 });
    expect(counts.controls).toEqual({ pageCount: 0 });
  });

  test("marks a section without a visible root as null", () => {
    const counts = sectionCounts([page("r1", "isms", null, null), page("x", "x", "r1", "r1")]);

    for (const slug of SECTION_SLUGS) {
      expect(counts[slug]).toBeNull();
    }
  });
});
