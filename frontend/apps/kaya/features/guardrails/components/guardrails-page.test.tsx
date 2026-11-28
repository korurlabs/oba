import { afterAll, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { NuqsTestingAdapter } from "nuqs/adapters/testing";
import { renderToStaticMarkup } from "react-dom/server";

import type { ReadyScope } from "@/features/scope/components";
import type { ScopeFile } from "@/features/scope/components/scope-files";
import type { EditSource } from "@/features/versions/hooks/use-change-write";

// The frame hands over a loaded scope in read mode with one plain rule and
// one inherited guardrail.
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
const GUARDRAIL: ScopeFile = {
  ...RULE,
  path: ".agents/rules/guardrails/no-push.md",
  versionId: "v-org",
  ownerId: "p-org",
};
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
  files: [RULE, GUARDRAIL],
  edit: null,
  origins: new Map(),
  effective: new Map([
    [
      GUARDRAIL.path,
      {
        path: GUARDRAIL.path,
        kind: "GUARDRAIL",
        sourcePackId: "p-org",
        sourceVersionId: "v-org",
        own: false,
        locked: "GUARDRAIL",
        override: null,
      },
    ],
  ]),
  inherited: true,
  inheritedFilter: { shown: true, onChange: () => undefined },
  owners: [owner("p-1", "village"), owner("p-org", "org")],
  release: null,
  scope: { kind: "PACK", packId: "p-1" },
} as unknown as ReadyScope;

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualScope = { ...(await import("@/features/scope/components")) };
let source: EditSource | null = { kind: "single", packId: "p-1", current: null };
const actualKinds = { ...(await import("@/features/rules/hooks/use-rule-kinds")) };
const actualNavigation = { ...(await import("next/navigation")) };
const actualSession = { ...(await import("@/features/versions/hooks/use-draft-session")) };
const actualWrite = { ...(await import("@/features/versions/hooks/use-change-write")) };
mock.module("@/features/scope/components", () => ({
  ...actualScope,
  ScopePage: ({ children }: { children: (ready: ReadyScope) => ReactNode }) => children(READY),
}));
mock.module("@/features/rules/hooks/use-rule-kinds", () => ({
  ...actualKinds,
  useRuleTabKind: () => ({ kind: null, error: null, refetch: () => undefined }),
}));
mock.module("next/navigation", () => ({
  ...actualNavigation,
  useRouter: () => ({ push: () => undefined }),
  useSearchParams: () => new URLSearchParams(),
}));
mock.module("@/features/versions/hooks/use-draft-session", () => ({
  ...actualSession,
  useDraftSession: () => ({}),
}));
mock.module("@/features/versions/hooks/use-change-write", () => ({
  ...actualWrite,
  useEditSource: () => source,
}));
afterAll(() => {
  mock.module("next/navigation", () => actualNavigation);
  mock.module("@/features/versions/hooks/use-draft-session", () => actualSession);
  mock.module("@/features/versions/hooks/use-change-write", () => actualWrite);
  mock.module("@/features/scope/components", () => actualScope);
  mock.module("@/features/rules/hooks/use-rule-kinds", () => actualKinds);
});

const { GuardrailsPage } = await import("./guardrails-page");

describe("GuardrailsPage", () => {
  test("lists only the guardrails as a table, with the lock and the pack of an inherited one", () => {
    const html = renderToStaticMarkup(
      <NuqsTestingAdapter>
        <GuardrailsPage />
      </NuqsTestingAdapter>
    );
    expect(html).toContain(GUARDRAIL.path);
    expect(html).not.toContain(RULE.path);
    expect(html).toContain('title="Guardrail, set by org"');
    expect(html).toContain(">From</th>");
    expect(html).toContain(">Loaded</th>");
    expect(html).toContain("from pack org");
  });

  test("a row links to the guardrail's own page", () => {
    const html = renderToStaticMarkup(
      <NuqsTestingAdapter>
        <GuardrailsPage />
      </NuqsTestingAdapter>
    );
    expect(html).toContain(`href="/guardrails/${encodeURIComponent(GUARDRAIL.path)}"`);
  });

  test("a pack scope offers New guardrail with its explanation in the button's tooltip, not a separate InfoTip", () => {
    const html = renderToStaticMarkup(
      <NuqsTestingAdapter>
        <GuardrailsPage />
      </NuqsTestingAdapter>
    );
    expect(html).toContain("New guardrail");
    expect(html).not.toContain('aria-label="What is a guardrail?"');
    expect(html).toContain('title="Filter"');
  });

  test("New guardrail is disabled with the lock tip while a change of another pack is open", () => {
    source = { kind: "locked", openPackName: "org" };
    const html = renderToStaticMarkup(
      <NuqsTestingAdapter>
        <GuardrailsPage />
      </NuqsTestingAdapter>
    );
    source = { kind: "single", packId: "p-1", current: null };
    expect(html).toMatch(/<button[^>]*disabled=""[^>]*>[^]*?New guardrail<\/button>/);
    expect(html).toContain('aria-label="Why is this disabled?"');
  });
});
