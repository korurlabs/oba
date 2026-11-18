import { afterAll, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { NuqsTestingAdapter } from "nuqs/adapters/testing";
import { renderToStaticMarkup } from "react-dom/server";

import type { ReadyScope } from "@/features/scope/components";
import type { ScopeFile } from "@/features/scope/components/scope-files";
import type { EditSource } from "@/features/versions/hooks/use-change-write";

// The page frame, the convention list and the edit source read through the
// query cache; the frame hands over a loaded scope, and the table is real.
function doc(path: string, ownerId = "p-1"): ScopeFile {
  return {
    path,
    mode: "FILE",
    sha256: null,
    size: null,
    target: null,
    alwaysOn: null,
    versionId: ownerId === "p-1" ? "v-1" : "v-org",
    ownerId,
  };
}
const INDEX = doc("docs/private/conventions/_index.md");
const CQRS = doc("docs/private/conventions/backend/03-service-cqrs-convention.md");
const SECURITY = doc("docs/private/conventions/security/01-secrets.md", "p-org");
const owner = (id: string, name: string) => ({
  id,
  name,
  parentPackId: null,
  currentVersionId: null,
  required: false,
  mountPaths: [],
  repositoryIds: [],
});
const PACK = {
  files: [SECURITY, doc(".agents/rules/a.md"), CQRS, INDEX],
  edit: null,
  origins: new Map(),
  effective: new Map(),
  inherited: true,
  inheritedFilter: { shown: true, onChange: () => undefined },
  owners: [owner("p-1", "village"), owner("p-org", "org")],
  release: null,
  scope: { kind: "PACK", packId: "p-1" },
} as unknown as ReadyScope;
const REPOSITORY = {
  ...PACK,
  inherited: false,
  inheritedFilter: null,
  scope: { kind: "REPOSITORY", repositoryId: "r-1" },
} as unknown as ReadyScope;

let ready: ReadyScope = PACK;
let source: EditSource | null = { kind: "single", packId: "p-1", current: null };
const url = "scope=pack%3Ap-1&inherited=true&panel=links";

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualScope = { ...(await import("@/features/scope/components")) };
const actualNavigation = { ...(await import("next/navigation")) };
const actualSession = { ...(await import("@/features/versions/hooks/use-draft-session")) };
const actualWrite = { ...(await import("@/features/versions/hooks/use-change-write")) };
const actualConventions = { ...(await import("../hooks/use-conventions")) };
mock.module("@/features/scope/components", () => ({
  ...actualScope,
  ScopePage: ({ children }: { children: (ready: ReadyScope) => ReactNode }) => children(ready),
}));
mock.module("next/navigation", () => ({
  ...actualNavigation,
  useRouter: () => ({ push: () => undefined }),
  useSearchParams: () => new URLSearchParams(url),
}));
mock.module("@/features/versions/hooks/use-draft-session", () => ({
  ...actualSession,
  useDraftSession: () => ({}),
}));
mock.module("@/features/versions/hooks/use-change-write", () => ({
  ...actualWrite,
  useEditSource: () => source,
}));
mock.module("../hooks/use-conventions", () => ({
  ...actualConventions,
  useConventions: () => ({
    data: {
      data: [
        {
          id: "c-1",
          versionId: "v-1",
          path: "backend/03-service-cqrs-convention.md",
          title: "CQRS",
          sizeBytes: 1,
        },
      ],
    },
    error: null,
    refetch: async () => undefined,
  }),
}));
afterAll(() => {
  mock.module("@/features/scope/components", () => actualScope);
  mock.module("next/navigation", () => actualNavigation);
  mock.module("@/features/versions/hooks/use-draft-session", () => actualSession);
  mock.module("@/features/versions/hooks/use-change-write", () => actualWrite);
  mock.module("../hooks/use-conventions", () => actualConventions);
});

const { ConventionsPage } = await import("./conventions-page");

function render(scope: ReadyScope, open = ".,backend,security"): string {
  ready = scope;
  if (scope === PACK) source = { kind: "single", packId: "p-1", current: null };
  return renderToStaticMarkup(
    <NuqsTestingAdapter searchParams={open === "" ? "" : `?open=${open}`}>
      <ConventionsPage />
    </NuqsTestingAdapter>
  );
}

describe("ConventionsPage", () => {
  test("every folder starts closed: a toggle row with its count and no docs", () => {
    const html = render(PACK, "");
    // Three folder toggles and the toolbar's closed filter menu.
    expect(html.match(/aria-expanded="false"/g)?.length).toBe(4);
    expect(html).not.toContain('aria-expanded="true"');
    expect(html).toContain("backend/</span>");
    expect(html).not.toContain("_index.md");
    expect(html).not.toContain("03-service-cqrs");
    expect(html).toContain('aria-label="New file in backend/"');
  });

  test("the open parameter opens only the folders it names", () => {
    const html = render(PACK, "backend");
    expect(html.match(/aria-expanded="true"/g)?.length).toBe(1);
    expect(html).toContain("03-service-cqrs");
    expect(html).not.toContain("01-secrets.md");
  });

  test("the docs are one table, a header row per folder, in path order", () => {
    const html = render(PACK);
    expect(html).toContain('aria-label="Convention docs"');
    const order = [
      "docs/private/conventions/</span>",
      "_index.md",
      "backend/</span>",
      "03-service-cqrs",
      "security/</span>",
      "01-secrets.md",
    ].map((text: string) => html.indexOf(text));
    expect(order.every((at: number) => at !== -1)).toBe(true);
    expect([...order].sort((a: number, b: number) => a - b)).toEqual(order);
    expect(html).not.toContain(".agents/rules/a.md");
  });

  test("a row links to the doc's own page, keeping the scope but not the open panel", () => {
    const html = render(PACK);
    expect(html).toContain(
      `href="/conventions/${encodeURIComponent(CQRS.path)}?scope=pack%3Ap-1&amp;inherited=true"`
    );
    expect(html).toContain(">CQRS</td>");
  });

  test("an inherited doc names its pack; the pack's own doc names none", () => {
    const html = render(PACK);
    expect(html).toContain("from pack org");
    expect(html).not.toContain("from pack village");
  });

  test("a pack offers New convention and one per folder; a repository's release offers none", () => {
    const pack = render(PACK);
    expect(pack).toContain("New convention");
    expect(pack).toContain('aria-label="New file in backend/"');
    const repository = render(REPOSITORY);
    expect(repository).not.toContain("New convention");
    expect(repository).not.toContain('aria-label="Filter');
    expect(repository).toContain("from pack village");
  });

  test("with a change of another pack open, New convention is disabled with the lock tip and no folder offers one", () => {
    ready = PACK;
    source = { kind: "locked", openPackName: "org" };
    const html = renderToStaticMarkup(
      <NuqsTestingAdapter searchParams="?open=.,backend,security">
        <ConventionsPage />
      </NuqsTestingAdapter>
    );
    expect(html).toMatch(/<button[^>]*disabled=""[^>]*>[^]*?New convention<\/button>/);
    expect(html).toContain('aria-label="Why is this disabled?"');
    expect(html).not.toContain('aria-label="New file in backend/"');
  });

  test("while the edit source loads, New convention is disabled and shows no lock tip", () => {
    ready = PACK;
    source = null;
    const html = renderToStaticMarkup(
      <NuqsTestingAdapter searchParams="?open=.,backend,security">
        <ConventionsPage />
      </NuqsTestingAdapter>
    );
    expect(html).toMatch(/<button[^>]*disabled=""[^>]*>[^]*?New convention<\/button>/);
    expect(html).not.toContain("Why is this disabled?");
    expect(html).not.toContain('aria-label="New file in backend/"');
  });
});
