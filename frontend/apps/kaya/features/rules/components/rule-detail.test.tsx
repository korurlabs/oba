import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { NuqsTestingAdapter } from "nuqs/adapters/testing";
import { renderToStaticMarkup } from "react-dom/server";

import type { ReadyScope } from "@/features/scope/components";
import type { ScopeFile } from "@/features/scope/components/scope-files";
import type { EditSource } from "@/features/versions/hooks/use-change-write";

// The frame hands over a loaded scope; the reads of the query cache, the edit
// source and the router are replaced. The header, the
// arrows, the tabs and the Links panel are real; the editor is a stub that
// records the props the page gives it, since a static render cannot click.
const owner = (id: string, name: string, mountPaths: string[] = []) => ({
  id,
  name,
  parentPackId: null,
  currentVersionId: "v-cur",
  required: false,
  mountPaths,
  repositoryIds: [],
});
const VILLAGE = owner("p-1", "village");
const ORG = owner("p-org", "org");

function file(path: string, overrides: Partial<ScopeFile> = {}): ScopeFile {
  return {
    path,
    mode: "FILE",
    sha256: null,
    size: null,
    target: null,
    alwaysOn: null,
    versionId: "v-1",
    ownerId: "p-1",
    ...overrides,
  };
}

const AGENT_A = ".claude/agents/a.md";
const AGENT_B = ".claude/agents/b.md";
const AGENT_C = ".claude/agents/c.md";
const RULE = ".agents/rules/11-backend-rules.md";

function scopeOf(files: ScopeFile[], extra: Record<string, unknown> = {}): ReadyScope {
  return {
    files,
    edit: null,
    origins: new Map(),
    effective: new Map(),
    inherited: true,
    owners: [VILLAGE, ORG],
    release: null,
    scope: { kind: "PACK", packId: "p-1" },
    ...extra,
  } as unknown as ReadyScope;
}

type Recorded = {
  editor: Record<string, unknown> | null;
  newEditor: Record<string, unknown> | null;
  buttons: Map<string, () => void>;
  push: string[];
  replace: string[];
  forceEditing: boolean;
};
const seen: Recorded = {
  editor: null,
  newEditor: null,
  buttons: new Map(),
  push: [],
  replace: [],
  forceEditing: false,
};

let ready: ReadyScope = scopeOf([]);
let url = "scope=pack%3Ap-1&inherited=true";
let target: unknown = VILLAGE;
const SINGLE: EditSource = { kind: "single", packId: "p-1", current: null };
const CHANGE: EditSource = { kind: "change", draft: { id: "d-1", packId: "p-1" } as never };
let source: EditSource | null = SINGLE;
let kindRows: unknown = null;
let dependencies: unknown;

const DRAFT = { id: "d-1", decidedAt: null };

const actualReact = { ...(await import("react")) };
const actualScope = { ...(await import("@/features/scope/components")) };
const actualNavigation = { ...(await import("next/navigation")) };
const actualSession = { ...(await import("@/features/versions/hooks/use-draft-session")) };
const actualWrite = { ...(await import("@/features/versions/hooks/use-change-write")) };
const actualKinds = { ...(await import("../hooks/use-rule-kinds")) };
const actualChanges = { ...(await import("@/features/changes/hooks/use-changes")) };
const actualVersions = { ...(await import("@/features/versions/hooks/use-versions")) };
const actualSettings = { ...(await import("@/features/members/hooks/use-members")) };
const actualDependencies = { ...(await import("@/features/links/hooks/use-dependencies")) };
const actualEditor = { ...(await import("@/features/versions/components/file-editor")) };
const actualViewer = { ...(await import("@/features/versions/components/file-viewer")) };
const actualNotes = { ...(await import("@/features/versions/components/override-status")) };
const actualEntity = { ...(await import("@/features/editor/components/entity-document")) };
const actualButton = { ...(await import("@oba/ui/sky/button")) };

mock.module("react", () => ({
  ...actualReact,
  // The editor view is local state set by a click; a static render starts it
  // by answering true to the one `useState(false)` the page opens with.
  useState: (initial: unknown) =>
    actualReact.useState(initial === false && seen.forceEditing ? true : initial),
}));
mock.module("@/features/scope/components", () => ({
  ...actualScope,
  ScopePage: ({ children }: { children: (value: ReadyScope) => ReactNode }) => children(ready),
}));
mock.module("next/navigation", () => ({
  ...actualNavigation,
  useRouter: () => ({
    push: (to: string) => seen.push.push(to),
    replace: (to: string) => seen.replace.push(to),
  }),
  useSearchParams: () => new URLSearchParams(url),
}));
mock.module("@/features/versions/hooks/use-draft-session", () => ({
  ...actualSession,
  useDraftSession: () => ({ target, setDraftId: () => undefined }),
}));
mock.module("@/features/versions/hooks/use-change-write", () => ({
  ...actualWrite,
  useEditSource: () => source,
}));
mock.module("../hooks/use-rule-kinds", () => ({
  ...actualKinds,
  useRuleTabKind: () => ({ kind: kindRows, error: null, refetch: () => undefined }),
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
  useDependencies: () => ({
    data: dependencies === undefined ? undefined : { success: true, data: dependencies },
    error: null,
    refetch: async () => undefined,
  }),
}));
mock.module("@/features/versions/components/file-editor", () => ({
  ...actualEditor,
  FileEditor: (props: Record<string, unknown>) => {
    if (props.file === null) seen.newEditor = props;
    else seen.editor = props;
    return <div>File editor {(props.source as EditSource).kind}</div>;
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
mock.module("@/features/editor/components/entity-document", () => ({
  ...actualEntity,
  OlderVersion: ({ at }: { at: string }) => <div>Older version {at}</div>,
}));
mock.module("@oba/ui/sky/button", () => ({
  ...actualButton,
  Button: ({
    children,
    onClick,
    disabled,
    asChild,
    ...rest
  }: {
    children: ReactNode;
    onClick?: () => void;
    disabled?: boolean;
    asChild?: boolean;
    "aria-label"?: string;
  }) => {
    if (asChild) return children;
    const label = rest["aria-label"] ?? "Edit";
    if (onClick) seen.buttons.set(label, onClick);
    return (
      <button aria-label={rest["aria-label"]} disabled={disabled}>
        {children}
      </button>
    );
  },
}));
afterAll(() => {
  mock.module("react", () => actualReact);
  mock.module("@/features/scope/components", () => actualScope);
  mock.module("next/navigation", () => actualNavigation);
  mock.module("@/features/versions/hooks/use-draft-session", () => actualSession);
  mock.module("@/features/versions/hooks/use-change-write", () => actualWrite);
  mock.module("../hooks/use-rule-kinds", () => actualKinds);
  mock.module("@/features/changes/hooks/use-changes", () => actualChanges);
  mock.module("@/features/versions/hooks/use-versions", () => actualVersions);
  mock.module("@/features/members/hooks/use-members", () => actualSettings);
  mock.module("@/features/links/hooks/use-dependencies", () => actualDependencies);
  mock.module("@/features/versions/components/file-editor", () => actualEditor);
  mock.module("@/features/versions/components/file-viewer", () => actualViewer);
  mock.module("@/features/versions/components/override-status", () => actualNotes);
  mock.module("@/features/editor/components/entity-document", () => actualEntity);
  mock.module("@oba/ui/sky/button", () => actualButton);
});

const { RuleDetail } = await import("./rule-detail");

function render(
  tab: "rules" | "agents-md" | "roles" | "subagents" | "workflows" | "templates" | "hooks",
  path: string | null,
  search = url
): string {
  url = search;
  return renderToStaticMarkup(
    <NuqsTestingAdapter searchParams={search}>
      <RuleDetail tab={tab} path={path} />
    </NuqsTestingAdapter>
  );
}

const AGENTS = [file(AGENT_A), file(AGENT_B), file(AGENT_C)];

beforeEach(() => {
  ready = scopeOf(AGENTS);
  url = "scope=pack%3Ap-1&inherited=true";
  target = VILLAGE;
  kindRows = null;
  dependencies = undefined;
  seen.editor = null;
  seen.newEditor = null;
  seen.buttons = new Map();
  seen.push = [];
  seen.replace = [];
  source = SINGLE;
  seen.forceEditing = false;
});

describe("RuleDetail path resolution", () => {
  test("an own file shows its path, state and the Content tab", () => {
    const html = render("subagents", AGENT_B);
    expect(html).toContain(`<h1 class="min-w-0 font-mono text-base break-all">${AGENT_B}</h1>`);
    expect(html).toContain("Viewer of .claude/agents/b.md");
    for (const tab of ["Content", "Links", "History", "Checks"]) {
      expect(html).toContain(`>${tab}</button>`);
    }
    expect(html).toContain("Override note");
  });

  test("a file only the open change holds resolves by its path", () => {
    const draftOnly = file(".claude/agents/new.md", { versionId: "v-draft" });
    ready = scopeOf([...AGENTS, draftOnly], { edit: { target: VILLAGE, draft: DRAFT } });
    const html = render("subagents", draftOnly.path);
    expect(html).toContain(`aria-label="${draftOnly.path}"`);
    expect(html).toContain(">Open change<");
    expect(html).not.toContain("does not deliver");
  });

  test("an inherited file resolves with the inherited note and no Edit", () => {
    const inherited = file(".claude/agents/org.md", { ownerId: "p-org", versionId: "v-org" });
    ready = scopeOf([...AGENTS, inherited]);
    const html = render("subagents", inherited.path);
    expect(html).toContain("Inherited note");
    expect(html).toContain(">Inherited<");
    expect(html).not.toContain("lucide-pencil");
  });

  test("a file that fits no tab still opens from the link's tab, without arrows", () => {
    ready = scopeOf([file("scripts/run.sh")]);
    const html = render("hooks", "scripts/run.sh");
    expect(html).toContain('aria-label="scripts/run.sh"');
    expect(html).not.toContain("Next file");
  });

  test("a path the scope does not deliver names it and links back to the list", () => {
    const html = render("subagents", ".claude/agents/gone.md", "scope=pack%3Ap-1&release=4");
    expect(html).toContain("This scope does not deliver");
    expect(html).toContain(".claude/agents/gone.md");
    expect(html).toContain('href="/rules?scope=pack%3Ap-1&amp;release=4&amp;tab=subagents"');
    expect(html).toContain("Back to Rules");
    expect(html).not.toContain("<h1");
  });
});

describe("RuleDetail header", () => {
  test("Back to Rules returns to the same scope, release, inherited setting and tab, dropping a draft parameter", () => {
    const html = render(
      "subagents",
      AGENT_B,
      "scope=pack%3Ap-1&release=4&draft=d-1&inherited=true&at=v-9&panel=links"
    );
    expect(html).toContain(
      'href="/rules?scope=pack%3Ap-1&amp;release=4&amp;inherited=true&amp;tab=subagents"'
    );
  });

  test("the arrows lead to the neighbouring rows in list order", () => {
    const html = render("subagents", AGENT_B);
    expect(html).toContain(
      `href="/rules/subagents/${encodeURIComponent(AGENT_A)}?scope=pack%3Ap-1&amp;inherited=true"`
    );
    expect(html).toContain(
      `href="/rules/subagents/${encodeURIComponent(AGENT_C)}?scope=pack%3Ap-1&amp;inherited=true"`
    );
    expect(html).toContain(`aria-label="Previous file: ${AGENT_A}"`);
    expect(html).toContain(`aria-label="Next file: ${AGENT_C}"`);
  });

  test("the first row disables Previous and the last row disables Next", () => {
    const first = render("subagents", AGENT_A);
    expect(first).toMatch(/<button[^>]*aria-label="Previous file"[^>]*disabled/);
    expect(first).toContain(`href="/rules/subagents/${encodeURIComponent(AGENT_B)}`);
    const last = render("subagents", AGENT_C);
    expect(last).toMatch(/<button[^>]*aria-label="Next file"[^>]*disabled/);
    expect(last).not.toMatch(/<button[^>]*aria-label="Previous file"[^>]*disabled/);
  });

  test("a subagent shows its name, model, turns and tools in the header", () => {
    kindRows = {
      tab: "subagents",
      rows: [
        {
          id: "s-1",
          versionId: "v-1",
          path: "b.md",
          name: "Builder",
          description: "Builds things",
          model: "opus",
          maxTurns: 50,
          tools: ["Bash", "Edit"],
          sizeBytes: 12,
        },
      ],
    };
    const html = render("subagents", AGENT_B);
    expect(html).toContain("Builder");
    expect(html).toContain("Model opus · At most 50 turns · Tools: Bash, Edit");
  });

  test("a rule shows the always-on badge from its rule row, an executable hook its badge", () => {
    ready = scopeOf([file(RULE), file(".agents/hooks/a.sh", { mode: "EXECUTABLE" })]);
    kindRows = {
      tab: "rules",
      rows: [
        {
          id: "r-1",
          versionId: "v-1",
          path: "11-backend-rules.md",
          description: "Backend",
          paths: ["**/*.java"],
          alwaysOn: true,
          conventions: [{ conventionPath: "docs/a.md" }],
          sizeBytes: 3,
        },
      ],
    };
    const rule = render("rules", RULE);
    expect(rule).toContain(">Always on<");
    const hook = render("hooks", ".agents/hooks/a.sh");
    expect(hook).toContain(">Executable<");
  });
});

describe("RuleDetail header row", () => {
  test("the size leaves the header for the name's tooltip", () => {
    ready = scopeOf([file(AGENT_A), file(AGENT_B, { size: 2048 }), file(AGENT_C)]);
    const html = render("subagents", AGENT_B);
    expect(html).not.toContain("2,048 bytes");
    expect(html).toContain('data-slot="tooltip-trigger"');
  });

  test("the size and description tooltips open from the keyboard", () => {
    ready = scopeOf([file(AGENT_A), file(AGENT_B, { size: 2048 }), file(AGENT_C)]);
    kindRows = {
      tab: "subagents",
      rows: [
        {
          id: "s-1",
          versionId: "v-1",
          path: "b.md",
          name: "Builder",
          description: "Builds things",
          model: "opus",
          maxTurns: 50,
          tools: ["Bash", "Edit"],
          sizeBytes: 12,
        },
      ],
    };
    const html = render("subagents", AGENT_B);
    expect(html).toMatch(/<div tabindex="0"[^>]*data-slot="tooltip-trigger"><h1/);
    expect(html).toMatch(/<p tabindex="0"[^>]*data-slot="tooltip-trigger">Builder/);
  });

  test("a Rules file shows no path over its header", () => {
    expect(render("subagents", AGENT_B)).not.toContain('aria-label="Path"');
  });

  test("the Links tab shows its count once the links are read, the label alone before", () => {
    expect(render("subagents", AGENT_B)).toContain(">Links</button>");
    dependencies = {
      linksTo: [{ path: RULE, relation: "MARKDOWN_LINK", status: "FOUND" }],
      usedBy: [{ path: "AGENTS.md", relation: "MARKDOWN_LINK", status: "FOUND" }],
    };
    expect(render("subagents", AGENT_B)).toContain(">Links 2</button>");
  });
});

describe("RuleDetail Links tab", () => {
  test("lists what the file links to and what uses it, a found rule opening its detail page", () => {
    dependencies = {
      linksTo: [
        {
          path: RULE,
          relation: "MARKDOWN_LINK",
          status: "FOUND",
        },
        { path: ".agents/rules/missing.md", relation: "MARKDOWN_LINK", status: "BROKEN" },
      ],
      usedBy: [{ path: "AGENTS.md", relation: "MARKDOWN_LINK", status: "FOUND" }],
    };
    ready = scopeOf([...AGENTS, file(RULE), file("AGENTS.md")]);
    const html = render("subagents", AGENT_B, "scope=pack%3Ap-1&inherited=true&panel=links");
    expect(html).toContain("Links to");
    expect(html).toContain("Used by");
    expect(html).toContain(
      `href="/rules/rules/${encodeURIComponent(RULE)}?scope=pack%3Ap-1&amp;inherited=true"`
    );
    expect(html).toContain('href="/rules/agents-md/AGENTS.md?scope=pack%3Ap-1&amp;inherited=true"');
    expect(html).toContain("Missing in this scope");
    expect(html).not.toContain("/rules/rules/.agents%2Frules%2Fmissing.md");
    expect(html).not.toContain("Viewer of");
  });
});

describe("RuleDetail Edit", () => {
  test("shows for an own file of the edited pack", () => {
    expect(render("subagents", AGENT_B)).toContain("lucide-pencil");
  });

  test("is hidden for an inherited file, a repository scope, a symlink and an older version", () => {
    ready = scopeOf([file(AGENT_A, { ownerId: "p-org" })]);
    expect(render("subagents", AGENT_A)).not.toContain("lucide-pencil");

    ready = scopeOf(AGENTS, { scope: { kind: "REPOSITORY", repositoryId: "r-1" } });
    target = null;
    expect(render("subagents", AGENT_B)).not.toContain("lucide-pencil");

    target = VILLAGE;
    ready = scopeOf([file(AGENT_A, { mode: "SYMLINK", target: "../x.md" })]);
    expect(render("subagents", AGENT_A)).not.toContain("lucide-pencil");

    ready = scopeOf(AGENTS);
    expect(render("subagents", AGENT_B, "scope=pack%3Ap-1&at=v-0")).not.toContain("lucide-pencil");
  });

  test("with no change open, Edit is enabled and opens no change first", () => {
    const html = render("subagents", AGENT_B);
    expect(html).toMatch(/<button[^>]*>[^]*?lucide-pencil[^]*?Edit<\/button>/);
    expect(html).not.toMatch(/<button[^>]*disabled=""[^>]*>[^]*?Edit<\/button>/);
    expect(html).not.toContain("Why is this disabled?");
    expect(seen.buttons.has("Edit")).toBe(true);
  });

  test("with a change of this pack open, Edit is enabled too", () => {
    source = CHANGE;
    ready = scopeOf(AGENTS, { edit: { target: VILLAGE, draft: DRAFT } });
    const html = render("subagents", AGENT_B);
    expect(html).not.toMatch(/<button[^>]*disabled=""[^>]*>[^]*?Edit<\/button>/);
    expect(html).not.toContain("Why is this disabled?");
  });

  test("with a change of another pack open, Edit is disabled and the lock tip stays reachable", () => {
    source = { kind: "locked", openPackName: "org" };
    const html = render("subagents", AGENT_B);
    expect(html).toMatch(/<button[^>]*disabled=""[^>]*>[^]*?Edit<\/button>/);
    expect(html).toContain('aria-label="Why is this disabled?"');
  });

  test("while the edit source loads, Edit is disabled without a lock tip", () => {
    source = null;
    const html = render("subagents", AGENT_B);
    expect(html).toMatch(/<button[^>]*disabled=""[^>]*>[^]*?Edit<\/button>/);
    expect(html).not.toContain("Why is this disabled?");
  });
});

describe("RuleDetail editor view", () => {
  function editing(): string {
    seen.forceEditing = true;
    source = CHANGE;
    ready = scopeOf(AGENTS, { edit: { target: VILLAGE, draft: DRAFT } });
    return render("subagents", AGENT_B);
  }

  test("shows the editor in place of the tabs and arrows, writing into the open change", () => {
    const html = editing();
    expect(html).toContain("File editor change");
    expect(html).not.toContain("Viewer of");
    expect(html).not.toContain(">Checks</button>");
    expect(html).not.toContain("Next file");
    expect(html).not.toContain("lucide-pencil");
    expect(seen.editor?.file).toMatchObject({ path: AGENT_B });
    expect(seen.editor?.source).toBe(CHANGE);
    expect(typeof seen.editor?.onSubmitted).toBe("function");
  });

  test("a save under the same path replaces nothing", () => {
    editing();
    (seen.editor?.onSaved as (path: string) => void)(AGENT_B);
    expect(seen.replace).toEqual([]);
    expect(seen.push).toEqual([]);
  });

  test("a rename moves the URL to the new path, keeping scope and inherited", () => {
    editing();
    (seen.editor?.onSaved as (path: string) => void)(".claude/agents/renamed.md");
    expect(seen.replace).toEqual([
      `/rules/subagents/${encodeURIComponent(".claude/agents/renamed.md")}?scope=pack%3Ap-1&inherited=true`,
    ]);
  });

  test("a rename into another kind's folder opens that kind", () => {
    editing();
    (seen.editor?.onSaved as (path: string) => void)(".agents/rules/moved.md");
    expect(seen.replace[0]).toStartWith("/rules/rules/");
  });

  test("Cancel leaves without a prompt or navigation", () => {
    editing();
    const confirms: string[] = [];
    const previous = globalThis.confirm;
    (globalThis as { confirm?: unknown }).confirm = (text: string) => {
      confirms.push(text);
      return true;
    };
    (seen.editor?.onCancel as () => void)();
    (globalThis as { confirm?: unknown }).confirm = previous;
    expect(confirms).toEqual([]);
    expect(seen.push).toEqual([]);
  });

  test("Delete file returns to the list at the same tab", () => {
    editing();
    (seen.editor?.onDeleted as () => void)();
    expect(seen.push).toEqual(["/rules?scope=pack%3Ap-1&inherited=true&tab=subagents"]);
  });
});

describe("RuleDetail new file", () => {
  test("with no change open it opens a one-edit editor at the tab's start path", () => {
    const html = render("rules", null);
    expect(html).toContain('<h1 class="break-words">New rule</h1>');
    expect(html).toContain("File editor single");
    expect(html).not.toContain("Start a draft");
    expect(seen.newEditor).toMatchObject({
      source: SINGLE,
      file: null,
      initialPath: ".agents/rules/",
      initialExecutable: false,
    });
    expect(render("hooks", null)).toContain("File editor");
    expect(seen.newEditor).toMatchObject({
      initialPath: ".agents/hooks/",
      initialExecutable: true,
    });
  });

  test("with a change of the pack open the editor saves into it", () => {
    source = CHANGE;
    ready = scopeOf([], { edit: { target: VILLAGE, draft: DRAFT } });
    expect(render("rules", null)).toContain("File editor change");
    expect(seen.newEditor?.source).toBe(CHANGE);
  });

  test("a repository scope asks to choose a pack and opens no editor", () => {
    ready = scopeOf([], { scope: { kind: "REPOSITORY", repositoryId: "r-1" } });
    target = null;
    const html = render("rules", null, "scope=repository%3Ar-1");
    expect(html).toContain("Choose a pack to add a file.");
    expect(seen.newEditor).toBeNull();
  });

  test("while the edit source loads no editor is shown", () => {
    source = null;
    render("rules", null);
    expect(seen.newEditor).toBeNull();
  });

  test("the first save replaces the URL with the file's detail page", () => {
    render("rules", null);
    (seen.newEditor?.onSaved as (path: string) => void)(RULE);
    expect(seen.replace).toEqual([
      `/rules/rules/${encodeURIComponent(RULE)}?scope=pack%3Ap-1&inherited=true`,
    ]);
    expect(seen.push).toEqual([]);
  });

  test("Cancel returns to the list", () => {
    render("roles", null);
    (seen.newEditor?.onCancel as () => void)();
    expect(seen.push).toEqual(["/rules?scope=pack%3Ap-1&inherited=true&tab=roles"]);
  });

  test("a mounted pack cannot add hooks", () => {
    const mounted = owner("p-1", "kagi", ["projects/kagi"]);
    ready = scopeOf([], { owners: [mounted, ORG] });
    const html = render("hooks", null);
    expect(html).toContain("A pack mounted in a folder cannot add hooks");
    expect(seen.newEditor).toBeNull();
  });
});
