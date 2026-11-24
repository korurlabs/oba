import { afterAll, afterEach, beforeEach, describe, expect, mock, spyOn, test } from "bun:test";
import { existsSync } from "node:fs";
import { join } from "node:path";
import { QueryClient } from "@tanstack/react-query";
import { ApiError } from "@oba/shared/api/axios";
import { PAGE_KEYS } from "../hooks/query-keys";
import type { PageDetail, PageRecentItem, PageTrashItem } from "../schemas";

// The api calls are stand-ins; the result says whether the route renders the
// page or answers 404.
let pageResult: { data?: PageDetail; error?: unknown };
let trashResult: { data?: PageTrashItem[]; error?: unknown };
let trashCalls: string[];
let recentResult: { data?: PageRecentItem[]; error?: unknown };
let recentCalls: unknown[][];
// The logged failures are read from a stand-in: a test file that loads
// next/server earlier leaves a console.error that needs a request scope.
let logged: ReturnType<typeof spyOn<Console, "error">>;

// Copies, not the namespaces: mock.module rewrites a loaded module in place.
const actualApi = { ...(await import("../api")) };

mock.module("../api", () => ({
  ...actualApi,
  getPage: async () => {
    if (pageResult.error) throw pageResult.error;
    return { data: pageResult.data };
  },
  listTrash: async (locale: string) => {
    trashCalls.push(locale);
    if (trashResult.error) throw trashResult.error;
    return { data: trashResult.data };
  },
  listRecentPages: async (limit: number, locale: string) => {
    recentCalls.push([limit, locale]);
    if (recentResult.error) throw recentResult.error;
    return { data: recentResult.data };
  },
}));

afterAll(() => {
  mock.module("../api", () => actualApi);
});

const { prefetchPage, prefetchRecentPages, prefetchTrash, requirePageId } =
  await import("./prefetch");

const ORG = "org-1";
const NOT_FOUND = new ApiError("Not found", { status: 404, code: "KLK_NOT_001" });
const DELETED: PageTrashItem = {
  id: "page-1",
  title: "Access control",
  path: "policies/access",
  deletedAt: "2026-11-20T10:00:00Z",
  deletedByName: "Admin",
  descendantCount: 0,
};

beforeEach(() => {
  pageResult = {};
  trashResult = { data: [] };
  trashCalls = [];
  recentResult = { data: [] };
  recentCalls = [];
  logged = spyOn(console, "error").mockImplementation(() => undefined);
});

afterEach(() => {
  logged.mockRestore();
});

describe("prefetchPage", () => {
  test("a page the server shows is cached under the key usePage reads", async () => {
    const page = { id: "page-1" } as PageDetail;
    pageResult = { data: page };
    const client = new QueryClient();

    expect(await prefetchPage(client, ORG, "page-1", "en")).toBe(true);
    expect(client.getQueryData(PAGE_KEYS.detail(ORG, "page-1"))).toEqual({ data: page });
    expect(trashCalls).toEqual([]);
  });

  test("an unknown page answers 404", async () => {
    pageResult = { error: NOT_FOUND };

    expect(await prefetchPage(new QueryClient(), ORG, "missing", "en")).toBe(false);
    expect(trashCalls).toEqual(["EN"]);
  });

  test("a page in the caller's trash renders, with the trash cached for its restore", async () => {
    pageResult = { error: NOT_FOUND };
    trashResult = { data: [DELETED] };
    const client = new QueryClient();

    expect(await prefetchPage(client, ORG, "page-1", "tr")).toBe(true);
    expect(client.getQueryData(PAGE_KEYS.trash(ORG, "TR"))).toEqual({ data: [DELETED] });
  });

  test("a trash the caller may not read counts as not holding the page", async () => {
    pageResult = { error: NOT_FOUND };
    trashResult = { error: new ApiError("Forbidden", { status: 403, code: "KLK_SEC_001" }) };

    expect(await prefetchPage(new QueryClient(), ORG, "page-1", "en")).toBe(false);
    expect(logged).toHaveBeenCalledTimes(1);
  });

  test("any other failure leaves the page to the client", async () => {
    pageResult = { error: new ApiError("Server error", { status: 500 }) };

    expect(await prefetchPage(new QueryClient(), ORG, "page-1", "en")).toBe(true);
    expect(trashCalls).toEqual([]);
    expect(logged).toHaveBeenCalledTimes(1);
  });
});

describe("prefetchRecentPages", () => {
  test("caches the list under the key useRecentPages reads, in the content locale", async () => {
    const client = new QueryClient();

    await prefetchRecentPages(client, ORG, 10, "tr", "test");

    expect(recentCalls).toEqual([[10, "TR"]]);
    expect(client.getQueryData(PAGE_KEYS.recent(ORG, 10, "TR"))).toEqual({ data: [] });
  });

  test("a failure is logged and leaves the list to the client", async () => {
    recentResult = { error: new ApiError("Server error", { status: 500 }) };
    const client = new QueryClient();

    await prefetchRecentPages(client, ORG, 50, "en", "test");

    expect(client.getQueryData(PAGE_KEYS.recent(ORG, 50, "EN"))).toBeUndefined();
    expect(logged).toHaveBeenCalledTimes(1);
  });
});

describe("prefetchTrash", () => {
  test("caches the trash under the key useTrash reads, in the content locale", async () => {
    trashResult = { data: [DELETED] };
    const client = new QueryClient();

    await prefetchTrash(client, ORG, "en", "test");

    expect(trashCalls).toEqual(["EN"]);
    expect(client.getQueryData(PAGE_KEYS.trash(ORG, "EN"))).toEqual({ data: [DELETED] });
  });

  test("a guest's refusal caches nothing and is not logged", async () => {
    trashResult = { error: new ApiError("Forbidden", { status: 403, code: "ORG_SEC_001" }) };
    const client = new QueryClient();

    await prefetchTrash(client, ORG, "en", "test");

    expect(client.getQueryData(PAGE_KEYS.trash(ORG, "EN"))).toBeUndefined();
    expect(logged).not.toHaveBeenCalled();
  });

  test("any other failure is logged and leaves the trash to the client", async () => {
    trashResult = { error: new ApiError("Server error", { status: 500 }) };
    const client = new QueryClient();

    await prefetchTrash(client, ORG, "en", "test");

    expect(client.getQueryData(PAGE_KEYS.trash(ORG, "EN"))).toBeUndefined();
    expect(logged).toHaveBeenCalledTimes(1);
  });
});

describe("requirePageId", () => {
  test("lets a page id through", () => {
    expect(() => requirePageId("5c2d1e0f-3a4b-4c5d-8e9f-0a1b2c3d4e5f")).not.toThrow();
  });

  test.each([["policies"], ["page-1"], ["5c2d1e0f"], [""]])(
    "answers 404 for the segment %p",
    (segment) => {
      expect(() => requirePageId(segment)).toThrow("NEXT_HTTP_ERROR_FALLBACK;404");
    }
  );
});

// A route that calls notFound() answers HTTP 404 only while nothing has been streamed yet. A
// loading.tsx wraps the page of its own and every deeper segment in a
// Suspense boundary that streams the shell first, so the routes that answer
// 404 keep it out of every segment from the dashboard group down to them.
describe("404 routes", () => {
  const dashboard = join(import.meta.dir, "../../../app/[locale]/(dashboard)");

  test.each([
    [""],
    ["[section]"],
    ["pages"],
    ["pages/[pageId]"],
    ["pages/[pageId]/edit"],
    ["pages/[pageId]/history"],
    ["pages/[pageId]/permissions"],
  ])("no loading boundary in %p", (segment) => {
    expect(existsSync(join(dashboard, segment))).toBe(true);
    expect(existsSync(join(dashboard, segment, "loading.tsx"))).toBe(false);
  });
});
