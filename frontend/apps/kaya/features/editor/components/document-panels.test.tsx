import { afterAll, describe, expect, mock, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

import type { ChangeDetailResponse, Violation } from "@/features/changes/schemas";
import type { DependencyResponse } from "@/features/links/schemas";
import type { ScopeFile } from "@/features/scope/components/scope-files";
import type { FileDiffResponse } from "@/features/versions/schemas";

// The panels read the draft's change, the file's dependencies and a path diff
// through the query cache; those reads are replaced, the panels' rendering is real.
let violations: readonly Violation[] | null | undefined;
let dependencies: DependencyResponse | undefined;
let diff: FileDiffResponse[] | undefined;
const diffCalls: Array<[string, string | undefined, string | undefined]> = [];

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualNavigation = { ...(await import("next/navigation")) };
const actualChanges = { ...(await import("@/features/changes/hooks/use-changes")) };
const actualDependencies = { ...(await import("@/features/links/hooks/use-dependencies")) };
const actualVersions = { ...(await import("@/features/versions/hooks/use-versions")) };

mock.module("next/navigation", () => ({
  ...actualNavigation,
  useSearchParams: () => new URLSearchParams("scope=pack"),
}));
mock.module("@/features/changes/hooks/use-changes", () => ({
  ...actualChanges,
  useChange: () => ({
    data: { success: true, data: { violations } as unknown as ChangeDetailResponse },
    error: null,
  }),
}));
mock.module("@/features/links/hooks/use-dependencies", () => ({
  ...actualDependencies,
  useDependencies: () => ({
    data: dependencies === undefined ? undefined : { success: true, data: dependencies },
    error: null,
  }),
}));
mock.module("@/features/versions/hooks/use-versions", () => ({
  ...actualVersions,
  useVersionDiff: (versionId: string, against?: string, path?: string) => {
    diffCalls.push([versionId, against, path]);
    return {
      data: diff === undefined ? undefined : { success: true, data: diff },
      error: null,
    };
  },
}));
afterAll(() => {
  mock.module("next/navigation", () => actualNavigation);
  mock.module("@/features/changes/hooks/use-changes", () => actualChanges);
  mock.module("@/features/links/hooks/use-dependencies", () => actualDependencies);
  mock.module("@/features/versions/hooks/use-versions", () => actualVersions);
});

const { ChecksPanel } = await import("./checks-panel");
const { DependencyPanel } = await import("./dependency-panel");
const { CompareView } = await import("./compare-view");

function file(path: string): ScopeFile {
  return {
    path,
    mode: "FILE",
    sha256: null,
    size: null,
    target: null,
    alwaysOn: null,
    versionId: "v-1",
    ownerId: "k-1",
  };
}

describe("ChecksPanel", () => {
  test("without a draft it says checks run on the viewer's own draft", () => {
    const html = renderToStaticMarkup(<ChecksPanel draftId={null} />);
    expect(html).toContain("Checks run on your own draft");
  });

  test("a draft with no violations may be submitted", () => {
    violations = [];
    const html = renderToStaticMarkup(<ChecksPanel draftId="d-1" />);
    expect(html).toContain("may be submitted");
    expect(html).not.toContain('role="alert"');
  });

  test("violations are listed with their path and message", () => {
    violations = [
      { path: ".agents/rules/x.md", rule: "PATH", message: "Name the <path> clearly" },
      { rule: "NAME" },
    ];
    const html = renderToStaticMarkup(<ChecksPanel draftId="d-1" />);
    expect(html).toContain('role="alert"');
    expect(html).toContain(".agents/rules/x.md");
    expect(html).toContain("Name the &lt;path&gt; clearly");
    expect(html).toContain("NAME");
  });
});

describe("DependencyPanel", () => {
  const CONVENTION = "docs/private/conventions/general/a.md";
  const files = [file(CONVENTION), file("docs/b.md")];

  test("an empty answer says it links to nothing and nothing uses it", () => {
    dependencies = { path: "docs/a.md", kind: "CONVENTION", linksTo: [], usedBy: [] };
    const html = renderToStaticMarkup(<DependencyPanel file={files[0]!} files={files} />);
    expect(html).toContain("It links to nothing, and nothing uses it.");
  });

  test("lists what the file links to and what uses it, with the relation and the status", () => {
    dependencies = {
      path: ".agents/rules/r.md",
      kind: "RULE",
      linksTo: [
        {
          path: CONVENTION,
          kind: null,
          relation: "RULE_CONVENTION",
          status: "FOUND",
          versionId: "v-1",
        },
        {
          path: "docs/gone.md",
          kind: null,
          relation: "MARKDOWN_LINK",
          status: "BROKEN",
          versionId: null,
        },
      ],
      usedBy: [
        {
          path: ".agents/skills/kaizen/SKILL.md",
          kind: "SKILL",
          relation: "SKILL_PART",
          status: "FOUND",
          versionId: "v-1",
        },
      ],
    };
    const html = renderToStaticMarkup(<DependencyPanel file={files[1]!} files={files} />);
    expect(html).toContain("Links to");
    expect(html).toContain("Used by");
    expect(html).toContain("Convention the rule cites");
    expect(html).toContain("Markdown link");
    expect(html).toContain("Skill made of this part");
    expect(html).toContain("Missing in this scope");
    // A found entry is a link that keeps the scope; a missing one is plain text.
    expect(html).toMatch(
      /<a [^>]*href="[^"]*scope=pack[^"]*"[^>]*><code[^>]*>docs\/private\/conventions\/general\/a\.md/
    );
    expect(html).not.toMatch(/<a [^>]*><code[^>]*>docs\/gone\.md/);
  });
});

describe("CompareView", () => {
  test("reads the diff of one path between the two versions", () => {
    diffCalls.length = 0;
    diff = [];
    renderToStaticMarkup(
      <CompareView versionId="v-9" against="v-3" path="AGENTS.md" label="your draft" />
    );
    expect(diffCalls).toEqual([["v-9", "v-3", "AGENTS.md"]]);
  });

  test("an empty diff says there is no difference", () => {
    diff = [];
    const html = renderToStaticMarkup(
      <CompareView versionId="v-9" against="v-3" path="AGENTS.md" label="the current version" />
    );
    expect(html).toContain("No difference");
    expect(html).toContain("the current version");
  });

  test("a diff is shown for its path", () => {
    diff = [
      {
        path: "AGENTS.md",
        change: "MODIFIED",
        binary: false,
        unifiedDiff: "--- a/AGENTS.md\n+++ b/AGENTS.md\n@@ -1 +1 @@\n-old\n+new\n",
      },
    ];
    const html = renderToStaticMarkup(
      <CompareView versionId="v-9" against="v-3" path="AGENTS.md" label="your draft" />
    );
    expect(html).toContain("From the version you opened to your draft.");
    expect(html).toContain("AGENTS.md");
    expect(html).toContain("new");
  });
});
