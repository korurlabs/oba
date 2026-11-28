import { describe, expect, test } from "bun:test";

import { ORGANIZATION_WIDE, type LessonFilterParams } from "../hooks/use-lesson-filter-params";
import { lessonFilterCategories } from "./lesson-filters";

const DEFAULTS: LessonFilterParams = { search: null, repositoryId: null, userId: null, page: 1 };

describe("the lesson list's filter categories", () => {
  test("Repository offers Organization-wide first and keeps a chosen value it no longer lists", () => {
    const [repository] = lessonFilterCategories({ ...DEFAULTS, repositoryId: "gone" }, () => {}, {
      repositories: [{ id: "r1", name: "oba" }],
      users: [],
    } as unknown as Parameters<typeof lessonFilterCategories>[2]);
    expect(repository?.options.map((option) => option.value)).toEqual([
      ORGANIZATION_WIDE,
      "r1",
      "gone",
    ]);
  });
});
