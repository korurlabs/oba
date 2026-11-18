import { afterAll, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { NuqsTestingAdapter } from "nuqs/adapters/testing";
import { renderToStaticMarkup } from "react-dom/server";

import type { ReadyScope } from "@/features/scope/components";
import type { ScopeFile } from "@/features/scope/components/scope-files";
import type { EditSource } from "@/features/versions/hooks/use-change-write";

// The page frame, the rule rows and the edit source read through the query
// cache; the frame hands over a loaded scope in read mode, and the file
// table is real.
const RULE: ScopeFile = {
  path: ".agents/rules/11-backend-rules.md",
  mode: "FILE",
  sha256: null,
  size: null,
  target: null,
  alwaysOn: null,
  versionId: "v-1",
  ownerId: "p-1",
};
const INHERITED: ScopeFile = {
  ...RULE,
  path: ".agents/rules/guardrails/no-push.md",
  versionId: "v-org",
  ownerId: "p-org",
};
const OVERRIDING: ScopeFile = { ...RULE, path: ".agents/rules/core.md" };
const owner = (id: string, name: string) => ({
  id,
  name,
  parentPackId: null,
  currentVersionId: null,
  required: false,
  mountPaths: [],
  repositoryIds: [],
});
const READY = {
  files: [OVERRIDING, RULE, INHERITED],
  edit: null,
  origins: new Map(),
  effective: new Map([
    [
      INHERITED.path,
      {
        path: INHERITED.path,
        kind: "GUARDRAIL",
        sourcePackId: "p-org",
        sourceVersionId: "v-org",
        own: false,
        locked: "GUARDRAIL",
        override: null,
      },
    ],
    [
      OVERRIDING.path,
      {
        path: OVERRIDING.path,
        kind: "RULE",
        sourcePackId: "p-1",
        sourceVersionId: "v-1",
        own: true,
        locked: null,
        override: {
          basePackId: "p-org",
          baseVersionId: "v-0",
          baseItemVersion: 4,
          parentVersionId: "v-org",
          parentItemVersion: 5,
          status: "PARENT_UPDATED",
        },
      },
    ],
  ]),
  inherited: true,
  inheritedFilter: { shown: true, onChange: () => undefined },
  owners: [owner("p-1", "village"), owner("p-org", "org")],
  release: null,
  scope: { kind: "PACK", packId: "p-1" },
} as unknown as ReadyScope;

// What the mocked frame hands the page, and the kind rows the mocked list read returns.
let ready: ReadyScope = READY;
let kindRows: unknown = null;
let kindError: Error | null = null;
let url = "scope=pack%3Ap-1&inherited=true";
let source: EditSource | null = { kind: "single", packId: "p-1", current: null };
const owners = (READY as unknown as { owners: ReturnType<typeof owner>[] }).owners;

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualScope = { ...(await import("@/features/scope/components")) };
const actualNavigation = { ...(await import("next/navigation")) };
const actualSession = { ...(await import("@/features/versions/hooks/use-draft-session")) };
const actualWrite = { ...(await import("@/features/versions/hooks/use-change-write")) };
const actualSettings = { ...(await import("@/features/allowlist/hooks/use-settings")) };
const actualKinds = { ...(await import("../hooks/use-rule-kinds")) };
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
mock.module("@/features/allowlist/hooks/use-settings", () => ({
  ...actualSettings,
  useSettings: () => ({ data: undefined }),
}));
mock.module("../hooks/use-rule-kinds", () => ({
  ...actualKinds,
  useRuleTabKind: () => ({ kind: kindRows, error: kindError, refetch: () => undefined }),
}));
afterAll(() => {
  mock.module("@/features/scope/components", () => actualScope);
  mock.module("next/navigation", () => actualNavigation);
  mock.module("@/features/versions/hooks/use-draft-session", () => actualSession);
  mock.module("@/features/versions/hooks/use-change-write", () => actualWrite);
  mock.module("@/features/allowlist/hooks/use-settings", () => actualSettings);
  mock.module("../hooks/use-rule-kinds", () => actualKinds);
});

const { RulesPage } = await import("./rules-page");

function render(search = url): string {
  url = search;
  return renderToStaticMarkup(
    <NuqsTestingAdapter searchParams={search}>
      <RulesPage />
    </NuqsTestingAdapter>
  );
}

function reset() {
  ready = READY;
  kindRows = null;
  kindError = null;
  url = "scope=pack%3Ap-1&inherited=true";
  source = { kind: "single", packId: "p-1", current: null };
}

describe("RulesPage", () => {
  test("each row links to its file's detail page, keeping the scope and inherited setting", () => {
    const html = renderToStaticMarkup(
      <NuqsTestingAdapter>
        <RulesPage />
      </NuqsTestingAdapter>
    );
    expect(html).toContain(
      `href="/rules/rules/${encodeURIComponent(RULE.path)}?scope=pack%3Ap-1&amp;inherited=true"`
    );
  });

  test("the inherited view names each file's pack, its override status and its lock", () => {
    const html = renderToStaticMarkup(
      <NuqsTestingAdapter>
        <RulesPage />
      </NuqsTestingAdapter>
    );
    expect(html).toContain("Overrides org");
    expect(html).toContain("Parent updated");
    expect(html).toContain('title="Guardrail, set by org"');
    expect(html).toContain(">from pack org<");
    expect(html).not.toContain("from pack village");
  });

  test("the list is a table of Path, Title, the tab's kind field and From, with no inline document or file badges", () => {
    reset();
    const html = render();
    for (const heading of ["Path", "Title", "Loaded", "From"]) {
      expect(html).toContain(`>${heading}</th>`);
    }
    expect(html).not.toContain("Links</");
    expect(html).not.toContain("History</");
    expect(html).not.toContain("Checks</");
    expect(html).not.toContain(">Executable<");
    expect(html).not.toContain(">Symlink<");
    expect(html).not.toContain(">Always on<");
  });

  test("a row shows the title and kind field of its kind row", () => {
    reset();
    kindRows = {
      tab: "rules",
      rows: [
        {
          id: "r-1",
          versionId: "v-1",
          path: "11-backend-rules.md",
          description: "Backend rules",
          paths: [],
          alwaysOn: true,
          conventions: [],
          sizeBytes: 2150,
        },
        {
          id: "r-2",
          versionId: "v-1",
          path: "core.md",
          description: null,
          paths: ["a/**", "b/**"],
          alwaysOn: false,
          conventions: [],
          sizeBytes: 5,
        },
      ],
    };
    const html = render();
    expect(html).toContain("Backend rules");
    expect(html).toContain("Always on, 2,150 bytes");
    expect(html).toContain("Applies to a/**, b/**");
  });

  test("From names a pack only on an inherited row of a pack scope", () => {
    reset();
    const html = render();
    expect(html.match(/from pack /g)).toHaveLength(1);
    expect(html).toContain(">from pack org<");
  });

  test("From names the pack of every row in a repository scope", () => {
    reset();
    ready = {
      ...READY,
      inheritedFilter: null,
      scope: { kind: "REPOSITORY", repositoryId: "r-1" },
    } as unknown as ReadyScope;
    const html = render("scope=repository%3Ar-1");
    expect(html.match(/from pack /g)).toHaveLength(3);
    expect(html).toContain(">from pack village<");
  });

  test("no scope shows an always-on budget card, in read mode or while a draft is edited", () => {
    const budget = /budget|of \d[\d,]* bytes/i;
    reset();
    const pack = render();
    expect(pack).toContain(".agents/rules/core.md");
    expect(pack).not.toMatch(budget);

    ready = {
      ...READY,
      inheritedFilter: null,
      scope: { kind: "REPOSITORY", repositoryId: "r-1" },
    } as unknown as ReadyScope;
    const repository = render("scope=repository%3Ar-1");
    expect(repository).toContain(".agents/rules/core.md");
    expect(repository).not.toMatch(budget);

    ready = {
      ...READY,
      edit: { target: owners[0], draft: { id: "d-1" } },
    } as unknown as ReadyScope;
    const editing = render();
    expect(editing).toContain(".agents/rules/core.md");
    expect(editing).not.toMatch(budget);
    reset();
  });

  test("New rule shows in a pack scope and not in a repository scope", () => {
    reset();
    expect(render()).toContain("New rule");
    ready = {
      ...READY,
      inheritedFilter: null,
      scope: { kind: "REPOSITORY", repositoryId: "r-1" },
    } as unknown as ReadyScope;
    expect(render("scope=repository%3Ar-1")).not.toContain("New rule");
    reset();
  });

  test("the create button is labelled by the tab", () => {
    reset();
    const labels: Record<string, string> = {
      rules: "New rule",
      "agents-md": "New AGENTS.md",
      roles: "New role",
      subagents: "New subagent",
      workflows: "New workflow",
      templates: "New template",
      hooks: "New hook",
    };
    for (const [tab, label] of Object.entries(labels)) {
      const html = render(`scope=pack%3Ap-1&tab=${tab}`);
      expect(html).toContain(`${label}</button>`);
      for (const other of Object.values(labels)) {
        if (other !== label) expect(html).not.toContain(`${other}</button>`);
      }
    }
    reset();
  });

  test("the toolbar has the filter and search, a repository scope has neither the filter nor New", () => {
    reset();
    const pack = render();
    expect(pack).toContain('aria-label="Filter');
    expect(pack).toContain('aria-label="Search files"');
    ready = {
      ...READY,
      inheritedFilter: null,
      scope: { kind: "REPOSITORY", repositoryId: "r-1" },
    } as unknown as ReadyScope;
    const repository = render("scope=repository%3Ar-1");
    expect(repository).not.toContain('aria-label="Filter');
    expect(repository).toContain('aria-label="Search files"');
    reset();
  });

  test("no count line and no source line render", () => {
    reset();
    const html = render();
    expect(html).not.toMatch(/\d+ (rules?|files?)\./);
    expect(html).not.toContain("Pack village: version");
    expect(html).not.toContain("Show inherited");
    reset();
  });

  test("New rule is disabled with the lock tip while a change of another pack is open", () => {
    reset();
    source = { kind: "locked", openPackName: "org" };
    const html = render();
    expect(html).toMatch(/<button[^>]*disabled=""[^>]*>[^]*?New rule<\/button>/);
    expect(html).toContain('aria-label="Why is this disabled?"');
    reset();
  });

  test("New rule is enabled with no lock tip on a one-edit or an open-change source, disabled while the source loads", () => {
    reset();
    const enabled = /<button(?![^>]*disabled="")[^>]*>[^]*?New rule<\/button>/;
    const html = render();
    expect(html).toMatch(enabled);
    expect(html).not.toContain("Why is this disabled?");
    source = { kind: "change", draft: { id: "d-1", packId: "p-1" } as never };
    expect(render()).toMatch(enabled);
    source = null;
    expect(render()).toMatch(/<button[^>]*disabled=""[^>]*>[^]*?New rule<\/button>/);
    reset();
  });

  test("a mounted pack's Hooks tab has the note instead of New rule", () => {
    reset();
    ready = {
      ...READY,
      owners: [{ ...owners[0], mountPaths: ["projects/kagi"] }, owners[1]],
    } as unknown as ReadyScope;
    const hooks = render("scope=pack%3Ap-1&tab=hooks");
    expect(hooks).toContain("A pack mounted in a folder cannot add hooks");
    expect(hooks).not.toContain("New rule");
    expect(render("scope=pack%3Ap-1&tab=rules")).toContain("New rule");
    reset();
  });

  test("a tab without files shows the empty text", () => {
    reset();
    const html = render("scope=pack%3Ap-1&tab=roles");
    expect(html).toContain("This scope delivers no roles.");
    expect(html).not.toContain("<table");
  });

  test("a failed kind list shows the load error and the rows still list by path", () => {
    reset();
    kindError = new Error("boom");
    const html = render();
    expect(html).toContain("Could not load the rules");
    expect(html).toContain(RULE.path);
    reset();
  });

  test("the Hooks tab says Not registered for a script no settings run", () => {
    reset();
    ready = {
      ...READY,
      files: [{ ...RULE, path: ".agents/hooks/notify.sh", mode: "EXECUTABLE" }],
    } as unknown as ReadyScope;
    const html = render("scope=pack%3Ap-1&tab=hooks");
    expect(html).toContain(">Events</th>");
    expect(html).toContain("Not registered");
    reset();
  });
});
