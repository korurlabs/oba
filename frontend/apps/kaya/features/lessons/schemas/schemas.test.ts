import { describe, expect, test } from "bun:test";

import {
  LESSON_LIMITS,
  lessonDetailResponseSchema,
  lessonFiltersResponseSchema,
  lessonListResponseSchema,
  lessonQueryRequestSchema,
  lessonRequestSchema,
} from ".";

const ROW = {
  id: "l-1",
  title: "Run Flyway first",
  body: "Apply the migration before the tests.",
  repository: { id: "r-1", name: "village" },
  user: { id: "u-1", name: "Ada" },
  claudeSessionId: "sess-1",
  session: { id: "s-1", sessionId: "sess-1", title: "Fix tests" },
  createdAt: "2026-10-25T09:00:00Z",
  updatedAt: "2026-10-25T09:00:00Z",
  canEdit: true,
};

describe("lesson query", () => {
  test("every field is optional", () => {
    expect(lessonQueryRequestSchema.parse({})).toEqual({});
  });

  test("the page starts at 1 and the amount is 1 to 100", () => {
    expect(lessonQueryRequestSchema.safeParse({ page: 0 }).success).toBe(false);
    expect(lessonQueryRequestSchema.safeParse({ amount: 0 }).success).toBe(false);
    expect(
      lessonQueryRequestSchema.safeParse({ amount: LESSON_LIMITS.pageSizeMax + 1 }).success
    ).toBe(false);
    expect(
      lessonQueryRequestSchema.safeParse({ page: 1, amount: LESSON_LIMITS.pageSizeMax }).success
    ).toBe(true);
  });

  test("the search holds at most 200 characters; organizationWide is a boolean", () => {
    expect(
      lessonQueryRequestSchema.safeParse({ search: "x".repeat(LESSON_LIMITS.searchMax) }).success
    ).toBe(true);
    expect(
      lessonQueryRequestSchema.safeParse({ search: "x".repeat(LESSON_LIMITS.searchMax + 1) })
        .success
    ).toBe(false);
    expect(lessonQueryRequestSchema.safeParse({ organizationWide: "true" }).success).toBe(false);
    expect(lessonQueryRequestSchema.parse({ organizationWide: true })).toEqual({
      organizationWide: true,
    });
  });
});

describe("lesson request", () => {
  test("title and text are trimmed", () => {
    expect(
      lessonRequestSchema.parse({ title: "  Rule  ", body: " Text \n", repositoryId: "r-1" })
    ).toEqual({ title: "Rule", body: "Text", repositoryId: "r-1" });
  });

  test("a blank title or text is refused with a message for the form", () => {
    const title = lessonRequestSchema.safeParse({ title: "  ", body: "x", repositoryId: null });
    expect(title.success).toBe(false);
    expect(title.error?.issues[0]?.message).toBe("Enter a title.");
    const body = lessonRequestSchema.safeParse({ title: "x", body: "", repositoryId: null });
    expect(body.error?.issues[0]?.message).toBe("Enter the text.");
  });

  test("the title holds at most 300 characters and the text at most 4000", () => {
    const ok = {
      title: "x".repeat(LESSON_LIMITS.titleMax),
      body: "x".repeat(LESSON_LIMITS.bodyMax),
    };
    expect(lessonRequestSchema.safeParse({ ...ok, repositoryId: null }).success).toBe(true);
    const longTitle = lessonRequestSchema.safeParse({
      ...ok,
      title: ok.title + "x",
      repositoryId: null,
    });
    expect(longTitle.error?.issues[0]?.message).toBe("A title has at most 300 characters.");
    const longBody = lessonRequestSchema.safeParse({
      ...ok,
      body: ok.body + "x",
      repositoryId: null,
    });
    expect(longBody.error?.issues[0]?.message).toBe("The text has at most 4000 characters.");
  });

  test("the repository is stated: null for the whole organization, never left out", () => {
    expect(
      lessonRequestSchema.safeParse({ title: "a", body: "b", repositoryId: null }).success
    ).toBe(true);
    expect(lessonRequestSchema.safeParse({ title: "a", body: "b" }).success).toBe(false);
  });
});

describe("lesson responses", () => {
  test("a row with a session and a repository parses", () => {
    expect(lessonListResponseSchema.parse(ROW)).toEqual(ROW);
  });

  test("a lesson of the whole organization has no repository, session or session id", () => {
    const row = {
      ...ROW,
      repository: null,
      session: null,
      claudeSessionId: null,
      user: { id: "u-2", name: null },
    };
    expect(lessonListResponseSchema.parse(row)).toEqual(row);
    const { repository, session, claudeSessionId, ...bare } = row;
    void repository;
    void session;
    void claudeSessionId;
    expect(lessonListResponseSchema.safeParse(bare).success).toBe(true);
  });

  test("a row without canEdit or with a wrong type is refused", () => {
    const { canEdit, ...withoutCanEdit } = ROW;
    void canEdit;
    expect(lessonListResponseSchema.safeParse(withoutCanEdit).success).toBe(false);
    expect(lessonListResponseSchema.safeParse({ ...ROW, canEdit: "yes" }).success).toBe(false);
  });

  test("the detail carries the same fields as a list row", () => {
    expect(lessonDetailResponseSchema.parse(ROW)).toEqual(ROW);
  });

  test("the filters hold repositories and users", () => {
    const filters = {
      repositories: [{ id: "r-1", name: "village" }],
      users: [{ id: "u-1", name: "Ada" }],
    };
    expect(lessonFiltersResponseSchema.parse(filters)).toEqual(filters);
    expect(lessonFiltersResponseSchema.safeParse({ repositories: [] }).success).toBe(false);
  });
});
