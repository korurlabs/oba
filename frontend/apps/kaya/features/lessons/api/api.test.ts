import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";

type Call = { method: string; url: string; body?: unknown; config?: unknown };

const calls: Call[] = [];
// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualClient = { ...(await import("@/lib/api/client")) };

const answer = async () => ({ data: { success: true, data: null } });

mock.module("@/lib/api/client", () => ({
  api: {
    get: async (url: string, config?: unknown) => {
      calls.push({ method: "GET", url, config });
      return answer();
    },
    post: async (url: string, body?: unknown) => {
      calls.push({ method: "POST", url, body });
      return answer();
    },
    put: async (url: string, body?: unknown) => {
      calls.push({ method: "PUT", url, body });
      return answer();
    },
    delete: async (url: string, config?: unknown) => {
      calls.push({ method: "DELETE", url, config });
      return answer();
    },
  },
}));

afterAll(() => {
  mock.module("@/lib/api/client", () => actualClient);
});

const lessons = await import(".");
const { ORGANIZATION_WIDE } = await import("../hooks/use-lesson-filter-params");
const { toLessonQuery } = await import("../components/lesson-data");

beforeEach(() => {
  calls.length = 0;
});

describe("lesson reads", () => {
  test("the list sends its filters as query params; filters and detail by path", async () => {
    await lessons.listLessons({ page: 2, amount: 25, repositoryId: "r-1", search: "flyway" });
    await lessons.getLessonFilters();
    await lessons.getLesson("l-1");
    expect(calls).toEqual([
      {
        method: "GET",
        url: "/kaya/lessons",
        config: { params: { page: 2, amount: 25, repositoryId: "r-1", search: "flyway" } },
      },
      { method: "GET", url: "/kaya/lessons/filters", config: undefined },
      { method: "GET", url: "/kaya/lessons/l-1", config: undefined },
    ]);
  });

  test("the organization-wide filter reaches the route as organizationWide true, with no repositoryId", async () => {
    const query = toLessonQuery({
      search: null,
      repositoryId: ORGANIZATION_WIDE,
      userId: null,
      page: 1,
    });
    await lessons.listLessons(query);
    expect(calls).toEqual([
      {
        method: "GET",
        url: "/kaya/lessons",
        config: { params: { page: 1, amount: 25, organizationWide: true } },
      },
    ]);
  });

  test("a lesson id is encoded in the path", async () => {
    await lessons.getLesson("a/b");
    expect(calls[0]?.url).toBe("/kaya/lessons/a%2Fb");
  });
});

describe("lesson writes", () => {
  test("create posts, update puts the whole lesson, delete removes it", async () => {
    const data = { title: "Run Flyway first", body: "Always.", repositoryId: null };
    await lessons.createLesson(data);
    await lessons.updateLesson("l-1", { ...data, repositoryId: "r-1" });
    await lessons.deleteLesson("l-1");
    expect(calls).toEqual([
      { method: "POST", url: "/kaya/lessons", body: data },
      { method: "PUT", url: "/kaya/lessons/l-1", body: { ...data, repositoryId: "r-1" } },
      { method: "DELETE", url: "/kaya/lessons/l-1", config: undefined },
    ]);
  });
});
