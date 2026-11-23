import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";
import { NextIntlClientProvider } from "next-intl";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ApiError } from "@oba/shared/api/axios";
import en from "@/locales/en.json";
import type { PageMoveRequest } from "@/features/pages/schemas";
import type { MoveStep } from "../lib/tree-moves";

// The api calls, the cache refresh and the toasts are stand-ins that record
// what the hook does; the hook and its mutation run for real. Each page
// answers with its version, which moves on by one with every move.
let versions: Record<string, number>;
let calls: string[];
let moves: { pageId: string; request: PageMoveRequest }[];
let failMoveOf: string | null;
let invalidations: number;
let toasts: { kind: string; message: string }[];

const actualApi = { ...(await import("@/features/pages/api")) };
const actualPages = { ...(await import("@/features/pages/hooks/use-pages")) };
const actualSonner = { ...(await import("sonner")) };

mock.module("@/features/pages/api", () => ({
  ...actualApi,
  getPage: async (pageId: string) => {
    calls.push(`get ${pageId}`);
    return { data: { id: pageId, version: versions[pageId] } };
  },
  movePage: async (pageId: string, request: PageMoveRequest) => {
    calls.push(`move ${pageId}`);
    if (pageId === failMoveOf) {
      throw new ApiError("Stale", { status: 409, code: "KLK_STA_002" });
    }
    moves.push({ pageId, request });
    versions[pageId] = (versions[pageId] ?? 0) + 1;
    return { data: { id: pageId } };
  },
}));
mock.module("@/features/pages/hooks/use-pages", () => ({
  ...actualPages,
  useInvalidatePages: () => () => {
    invalidations += 1;
  },
}));
mock.module("sonner", () => ({
  ...actualSonner,
  toast: {
    success: (message: string) => toasts.push({ kind: "success", message }),
    error: (message: string) => toasts.push({ kind: "error", message }),
  },
}));

afterAll(() => {
  mock.module("@/features/pages/api", () => actualApi);
  mock.module("@/features/pages/hooks/use-pages", () => actualPages);
  mock.module("sonner", () => actualSonner);
});

const { useMoveSectionPage } = await import("./use-move-section-page");

type Move = ReturnType<typeof useMoveSectionPage>;

/** The hook as a component sees it on its first render. */
function moveHook(): Move {
  let hook: Move | null = null;
  function Probe() {
    hook = useMoveSectionPage();
    return null;
  }
  renderToStaticMarkup(
    <QueryClientProvider client={new QueryClient()}>
      <NextIntlClientProvider locale="en" messages={en} timeZone="UTC">
        <Probe />
      </NextIntlClientProvider>
    </QueryClientProvider>
  );
  if (hook === null) throw new Error("The hook did not render");
  return hook;
}

const STEPS: MoveStep[] = [
  { pageId: "a", parentId: "root", position: 1 },
  { pageId: "b", parentId: "root", position: 0 },
];

/** Lets the mutation's callbacks, which run after the promise settles, finish. */
const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

beforeEach(() => {
  versions = { a: 3, b: 7 };
  calls = [];
  moves = [];
  failMoveOf = null;
  invalidations = 0;
  toasts = [];
});

describe("useMoveSectionPage", () => {
  test("reads each page's version right before its move, in order, and reports one move", async () => {
    await moveHook().mutateAsync(STEPS);
    await settle();

    expect(calls).toEqual(["get a", "move a", "get b", "move b"]);
    expect(moves).toEqual([
      { pageId: "a", request: { parentId: "root", position: 1, version: 3 } },
      { pageId: "b", request: { parentId: "root", position: 0, version: 7 } },
    ]);
    expect(toasts).toEqual([{ kind: "success", message: en.pages.toast.moved }]);
    expect(invalidations).toBe(1);
  });

  test("stops at a refused step, reports why, and still refreshes the tree", async () => {
    failMoveOf = "a";

    await expect(moveHook().mutateAsync(STEPS)).rejects.toBeInstanceOf(ApiError);
    await settle();

    expect(calls).toEqual(["get a", "move a"]);
    expect(toasts).toEqual([{ kind: "error", message: en.pages.errors.versionOutdated }]);
    expect(invalidations).toBe(1);
  });

  test("refuses a page that answers without a version before moving it", async () => {
    versions = { b: 7 };

    await expect(moveHook().mutateAsync(STEPS)).rejects.toThrow(
      "Page a answered without a version"
    );
    await settle();

    expect(moves).toEqual([]);
    expect(toasts.map((toast) => toast.kind)).toEqual(["error"]);
    expect(invalidations).toBe(1);
  });
});
