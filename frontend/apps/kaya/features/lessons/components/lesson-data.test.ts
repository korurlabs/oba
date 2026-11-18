import { describe, expect, test } from "bun:test";

import type { RepositoryResponse } from "@/features/sessions/schemas";

import { ORGANIZATION_WIDE, type LessonFilterParams } from "../hooks/use-lesson-filter-params";
import {
  emptyLessonsText,
  firstLine,
  hasActiveFilter,
  ORGANIZATION_WIDE_LABEL,
  referenceName,
  repositoryChoices,
  repositoryText,
  sessionText,
  toLessonQuery,
} from "./lesson-data";

const DEFAULTS: LessonFilterParams = { search: null, repositoryId: null, userId: null, page: 1 };

describe("toLessonQuery", () => {
  test("the default list is the first page of 25", () => {
    expect(toLessonQuery(DEFAULTS)).toEqual({ page: 1, amount: 25 });
  });

  test("ORGANIZATION_WIDE becomes organizationWide true and sends no repositoryId", () => {
    const query = toLessonQuery({ ...DEFAULTS, repositoryId: ORGANIZATION_WIDE });
    expect(query).toEqual({ page: 1, amount: 25, organizationWide: true });
    expect("repositoryId" in query).toBe(false);
  });

  test("a repository, a user and a trimmed search are passed on", () => {
    expect(
      toLessonQuery({ search: " flyway ", repositoryId: "r-1", userId: "u-1", page: 3 })
    ).toEqual({ page: 3, amount: 25, repositoryId: "r-1", userId: "u-1", search: "flyway" });
  });

  test("empty and blank filters are left out; a page below 1 falls back to 1", () => {
    expect(toLessonQuery({ search: "  ", repositoryId: "", userId: "", page: 0 })).toEqual({
      page: 1,
      amount: 25,
    });
  });
});

describe("empty list text", () => {
  test("a filter, a later page and a fresh organization each have their own text", () => {
    expect(hasActiveFilter(DEFAULTS)).toBe(false);
    expect(hasActiveFilter({ ...DEFAULTS, userId: "u-1" })).toBe(true);
    expect(emptyLessonsText({ ...DEFAULTS, search: "x" })).toBe("No lesson matches these filters.");
    expect(emptyLessonsText({ ...DEFAULTS, page: 2 })).toBe("This page holds no lesson.");
    expect(emptyLessonsText(DEFAULTS)).toBe("No lesson recorded yet.");
  });
});

describe("names and texts", () => {
  test("a reference without a name shows a short id", () => {
    expect(referenceName({ id: "abcdef123456", name: "Ada" })).toBe("Ada");
    expect(referenceName({ id: "abcdef123456", name: null })).toBe("abcdef12");
  });

  test("a lesson without a repository applies to All", () => {
    expect(repositoryText(null)).toBe("All");
    expect(repositoryText(undefined)).toBe("All");
    expect(repositoryText({ id: "r-1", name: "village" })).toBe("village");
  });

  test("firstLine skips blank lines and trims", () => {
    expect(firstLine("\n  \n  Apply it.  \nSecond")).toBe("Apply it.");
    expect(firstLine("   \n")).toBe("");
  });

  test("a session shows its title, or a short id when the title is empty", () => {
    expect(sessionText({ id: "s", sessionId: "abcdef123456", title: "Fix tests" })).toBe(
      "Fix tests"
    );
    expect(sessionText({ id: "s", sessionId: "abcdef123456", title: "  " })).toBe("abcdef12");
    expect(sessionText({ id: "s", sessionId: "abcdef123456", title: null })).toBe("abcdef12");
  });

  test("the organization-wide label", () => {
    expect(ORGANIZATION_WIDE_LABEL).toBe("Organization-wide");
  });
});

describe("repositoryChoices", () => {
  const repo = (id: string, name: string, status: string): RepositoryResponse =>
    ({ id, name, status }) as RepositoryResponse;
  const repositories = [
    repo("r-b", "beta", "ACTIVE"),
    repo("r-a", "alpha", "ACTIVE"),
    repo("r-x", "archived", "ARCHIVED"),
  ];

  test("archived repositories are left out and the rest sorted by name", () => {
    expect(repositoryChoices(repositories, null)).toEqual([
      { id: "r-a", name: "alpha" },
      { id: "r-b", name: "beta" },
    ]);
  });

  test("the lesson's own archived repository stays", () => {
    expect(repositoryChoices(repositories, { id: "r-x", name: "archived" })).toEqual([
      { id: "r-a", name: "alpha" },
      { id: "r-x", name: "archived" },
      { id: "r-b", name: "beta" },
    ]);
  });

  test("the lesson's repository is kept while the list has not loaded", () => {
    expect(repositoryChoices([], { id: "r-1", name: "village" })).toEqual([
      { id: "r-1", name: "village" },
    ]);
    expect(repositoryChoices([], { id: "abcdef123456", name: null })).toEqual([
      { id: "abcdef123456", name: "abcdef12" },
    ]);
  });
});
