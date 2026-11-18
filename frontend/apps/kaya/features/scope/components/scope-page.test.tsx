import { afterAll, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";

import type { DraftSession } from "@/features/versions/hooks/use-draft-session";

// The frame's reads are replaced; what stays above the page body is real.
let editing = false;

const SESSION: DraftSession = {
  scope: { kind: "PACK", packId: "k-1" },
  draftId: null,
  target: null,
  draft: null,
  draftLoading: false,
  stale: false,
  setDraftId: () => undefined,
};

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualNavigation = { ...(await import("next/navigation")) };
const actualScope = { ...(await import("../hooks/use-scope")) };
const actualContent = { ...(await import("../hooks/use-scope-content")) };
const actualEffective = { ...(await import("../hooks/use-effective")) };
const actualVersions = { ...(await import("@/features/versions/hooks/use-versions")) };
const actualSession = { ...(await import("@/features/versions/hooks/use-draft-session")) };
const actualTree = { ...(await import("./pack-tree")) };

mock.module("next/navigation", () => ({
  ...actualNavigation,
  usePathname: () => "/rules",
  useSearchParams: () => new URLSearchParams(""),
}));
mock.module("../hooks/use-scope", () => ({
  ...actualScope,
  useScope: () => ({
    scope: { kind: "PACK", packId: "k-1" },
    options: { repositories: [], packs: [{ value: "pack:k-1", label: "village" }] },
    owners: [],
    release: null,
    setScope: () => undefined,
    loading: false,
    error: null,
    retry: () => undefined,
  }),
}));
mock.module("../hooks/use-scope-content", () => ({
  ...actualContent,
  useScopeContent: () => ({
    status: "ready",
    files: [],
    owners: [],
    release: null,
    newestReleaseNumber: null,
    version: { number: 1, status: "APPROVED", files: [] },
  }),
}));
mock.module("../hooks/use-effective", () => ({
  ...actualEffective,
  useShowInherited: () => [false, () => undefined],
  usePackEffective: () => ({ data: { success: true, data: [] }, error: null }),
}));
mock.module("@/features/versions/hooks/use-versions", () => ({
  ...actualVersions,
  useVersionDetails: () => [],
}));
mock.module("@/features/versions/hooks/use-draft-session", () => ({
  ...actualSession,
  useDraftSession: () =>
    editing
      ? {
          ...SESSION,
          draftId: "d-2",
          target: { id: "k-1", name: "village" },
          draft: { id: "d-2", number: 2, files: [] },
        }
      : SESSION,
}));
mock.module("./pack-tree", () => ({ ...actualTree, PackTree: () => null }));
afterAll(() => {
  mock.module("next/navigation", () => actualNavigation);
  mock.module("../hooks/use-scope", () => actualScope);
  mock.module("../hooks/use-scope-content", () => actualContent);
  mock.module("../hooks/use-effective", () => actualEffective);
  mock.module("@/features/versions/hooks/use-versions", () => actualVersions);
  mock.module("@/features/versions/hooks/use-draft-session", () => actualSession);
  mock.module("./pack-tree", () => actualTree);
});

const { ScopePage } = await import("./scope-page");

function render(variant: "list" | "detail"): string {
  return renderToStaticMarkup(
    <ScopePage title="Rules" description="The rules." variant={variant}>
      {(): ReactNode => <article>document</article>}
    </ScopePage>
  );
}

const SOURCE_LINE = "Pack village: version 1 (approved), 0 files.";
const OLDER_RELEASE = "an older release";

describe("ScopePage frame", () => {
  test("a list shows its heading, with no source line and no Show inherited checkbox", () => {
    editing = false;
    const html = render("list");
    expect(html).not.toContain(SOURCE_LINE);
    expect(html).not.toContain(OLDER_RELEASE);
    expect(html).toContain("<h1>Rules</h1>");
    expect(html).not.toContain("Show inherited");
  });

  test("a document gets the source behind its label, above the page body", () => {
    editing = false;
    const html = render("detail");
    expect(html).not.toContain(SOURCE_LINE);
    expect(html).toContain(">Pack village</button>");
    expect(html.indexOf(">Pack village</button>")).toBeLessThan(html.indexOf("document"));
  });

  test("no page offers to start a draft, with or without an open change", () => {
    for (const open of [false, true]) {
      editing = open;
      for (const variant of ["list", "detail"] as const) {
        const html = render(variant);
        expect(html).not.toContain("Start a draft");
        expect(html).not.toContain("Save to draft");
        expect(html).toContain("document");
      }
    }
    editing = false;
  });
});
