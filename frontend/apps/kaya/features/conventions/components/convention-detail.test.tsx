import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { NuqsTestingAdapter } from "nuqs/adapters/testing";
import { renderToStaticMarkup } from "react-dom/server";

import type { ReadyScope } from "@/features/scope/components";
import type { ScopeFile } from "@/features/scope/components/scope-files";
import type { EditSource } from "@/features/versions/hooks/use-change-write";

// The frame hands over a loaded scope; the reads of the query cache, the edit
// source and the router are replaced. The header, the arrows and the tabs are
// real; the viewer and the editor are stubs, the editor recording its props.
const VILLAGE = {
  id: "p-1",
  name: "village",
  parentPackId: null,
  currentVersionId: "v-cur",
  required: false,
  mountPaths: [],
  repositoryIds: [],
};

function file(path: string): ScopeFile {
  return {
    path,
    mode: "FILE",
    sha256: null,
    size: null,
    target: null,
    alwaysOn: null,
    versionId: "v-1",
    ownerId: "p-1",
  };
}

const INDEX = "docs/private/conventions/_index.md";
const CQRS = "docs/private/conventions/backend/03-service-cqrs-convention.md";
const NESTED = "docs/private/conventions/backend/sub/x.md";
const LOGGING = "docs/private/conventions/backend/40-logging.md";

function scopeOf(extra: Record<string, unknown> = {}): ReadyScope {
  return {
    files: [file(NESTED), file(LOGGING), file(".agents/rules/a.md"), file(CQRS), file(INDEX)],
    edit: null,
    origins: new Map(),
    effective: new Map(),
    inherited: false,
    owners: [VILLAGE],
    release: null,
    scope: { kind: "PACK", packId: "p-1" },
    ...extra,
  } as unknown as ReadyScope;
}

let ready: ReadyScope = scopeOf();
let url = "scope=pack%3Ap-1";
let newEditor: Record<string, unknown> | null = null;
let source: EditSource | null = { kind: "single", packId: "p-1", current: null };

const actualScope = { ...(await import("@/features/scope/components")) };
const actualNavigation = { ...(await import("next/navigation")) };
const actualSession = { ...(await import("@/features/versions/hooks/use-draft-session")) };
const actualWrite = { ...(await import("@/features/versions/hooks/use-change-write")) };
const actualConventions = { ...(await import("../hooks/use-conventions")) };
const actualChanges = { ...(await import("@/features/changes/hooks/use-changes")) };
const actualVersions = { ...(await import("@/features/versions/hooks/use-versions")) };
const actualSettings = { ...(await import("@/features/members/hooks/use-members")) };
const actualDependencies = { ...(await import("@/features/links/hooks/use-dependencies")) };
const actualEditor = { ...(await import("@/features/versions/components/file-editor")) };
const actualViewer = { ...(await import("@/features/versions/components/file-viewer")) };
const actualNotes = { ...(await import("@/features/versions/components/override-status")) };

mock.module("@/features/scope/components", () => ({
  ...actualScope,
  ScopePage: ({ children }: { children: (value: ReadyScope) => ReactNode }) => children(ready),
}));
mock.module("next/navigation", () => ({
  ...actualNavigation,
  useRouter: () => ({ push: () => undefined, replace: () => undefined }),
  useSearchParams: () => new URLSearchParams(url),
}));
mock.module("@/features/versions/hooks/use-draft-session", () => ({
  ...actualSession,
  useDraftSession: () => ({ target: VILLAGE, setDraftId: () => undefined }),
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
mock.module("@/features/changes/hooks/use-changes", () => ({
  ...actualChanges,
  useChange: () => ({ data: undefined, error: null }),
  useFileHistory: () => ({ data: undefined, error: null }),
}));
mock.module("@/features/versions/hooks/use-versions", () => ({
  ...actualVersions,
  useVersion: () => ({ data: undefined, error: null }),
  useVersionFile: () => ({ data: undefined, error: null }),
}));
mock.module("@/features/members/hooks/use-members", () => ({
  ...actualSettings,
  useMembers: () => ({ data: undefined }),
}));
mock.module("@/features/links/hooks/use-dependencies", () => ({
  ...actualDependencies,
  useDependencies: () => ({ data: undefined, error: null, refetch: async () => undefined }),
}));
mock.module("@/features/versions/components/file-editor", () => ({
  ...actualEditor,
  FileEditor: (props: Record<string, unknown>) => {
    newEditor = props;
    return <div>File editor</div>;
  },
}));
mock.module("@/features/versions/components/file-viewer", () => ({
  ...actualViewer,
  FileViewer: ({ file: shown }: { file: ScopeFile }) => <div>Viewer of {shown.path}</div>,
}));
mock.module("@/features/versions/components/override-status", () => ({
  ...actualNotes,
  InheritedNote: () => <div>Inherited note</div>,
  OverrideNote: () => <div>Override note</div>,
}));
afterAll(() => {
  mock.module("@/features/scope/components", () => actualScope);
  mock.module("next/navigation", () => actualNavigation);
  mock.module("@/features/versions/hooks/use-draft-session", () => actualSession);
  mock.module("@/features/versions/hooks/use-change-write", () => actualWrite);
  mock.module("../hooks/use-conventions", () => actualConventions);
  mock.module("@/features/changes/hooks/use-changes", () => actualChanges);
  mock.module("@/features/versions/hooks/use-versions", () => actualVersions);
  mock.module("@/features/members/hooks/use-members", () => actualSettings);
  mock.module("@/features/links/hooks/use-dependencies", () => actualDependencies);
  mock.module("@/features/versions/components/file-editor", () => actualEditor);
  mock.module("@/features/versions/components/file-viewer", () => actualViewer);
  mock.module("@/features/versions/components/override-status", () => actualNotes);
});

const { ConventionDetail } = await import("./convention-detail");

function render(path: string | null, search = url): string {
  url = search;
  return renderToStaticMarkup(
    <NuqsTestingAdapter searchParams={search}>
      <ConventionDetail path={path} />
    </NuqsTestingAdapter>
  );
}

const href = (path: string) => `/conventions/${encodeURIComponent(path)}?scope=pack%3Ap-1`;

beforeEach(() => {
  ready = scopeOf();
  url = "scope=pack%3Ap-1";
  newEditor = null;
  source = { kind: "single", packId: "p-1", current: null };
});

describe("ConventionDetail", () => {
  test("a doc shows its path, title, the back link, Edit and the four tabs with Content open", () => {
    const html = render(CQRS);
    expect(html).toContain(`>${CQRS}</h1>`);
    expect(html).toContain(">CQRS</p>");
    // Back to Conventions opens the doc's folder.
    expect(html).toContain('href="/conventions?scope=pack%3Ap-1&amp;open=backend"');
    expect(html).toContain("Back to Conventions");
    expect(html).toContain("Edit");
    for (const tab of ["Content", "Links", "History", "Checks"]) expect(html).toContain(`>${tab}<`);
    expect(html).toContain(`Viewer of ${CQRS}`);
  });

  test("the arrows follow the list: a folder's own docs before its nested folder", () => {
    const html = render(LOGGING);
    expect(html).toContain(`href="${href(CQRS)}"`);
    expect(html).toContain(`href="${href(NESTED)}"`);
    expect(html).toContain(`aria-label="Previous file: ${CQRS}"`);
    expect(html).toContain(`aria-label="Next file: ${NESTED}"`);
  });

  test("the first doc has no previous one", () => {
    const html = render(INDEX);
    expect(html).toMatch(
      /<button[^>]*disabled=""[^>]*aria-label="Previous file"|<button[^>]*aria-label="Previous file"[^>]*disabled=""/
    );
    expect(html).toContain(`href="${href(CQRS)}"`);
  });

  test("a path the scope does not deliver says so under the back link", () => {
    const html = render("docs/private/conventions/gone.md");
    expect(html).toContain("Back to Conventions");
    expect(html).toContain("This scope does not deliver");
  });

  test("a new doc starts in the folder the address names, with a one-edit source", () => {
    render(null, "scope=pack%3Ap-1&folder=docs%2Fprivate%2Fconventions%2Fbackend%2F");
    expect(newEditor?.initialPath).toBe("docs/private/conventions/backend/");
    expect(newEditor?.source).toBe(source);
    render(null, "scope=pack%3Ap-1&folder=docs%2Fprivate%2Fconventions%2Fgone%2F");
    expect(newEditor?.initialPath).toBe("docs/private/conventions/");
  });

  test("a new doc inside the open change is saved into it", () => {
    source = { kind: "change", draft: { id: "d-1", packId: "p-1" } as never };
    render(null, "scope=pack%3Ap-1");
    expect(newEditor?.source).toBe(source);
  });

  test("a repository scope asks to choose a pack and starts no editor", () => {
    ready = scopeOf({ scope: { kind: "REPOSITORY", repositoryId: "r-1" } });
    source = null;
    const html = render(null, "scope=repository%3Ar-1");
    expect(html).toContain("Choose a pack to add a file.");
    expect(html).not.toContain("Start a draft");
    expect(newEditor).toBeNull();
  });

  test("while the edit source loads no editor is shown", () => {
    source = null;
    expect(render(null, "scope=pack%3Ap-1")).not.toContain("File editor");
  });
});
