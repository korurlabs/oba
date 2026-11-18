import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { NuqsTestingAdapter } from "nuqs/adapters/testing";
import { renderToStaticMarkup } from "react-dom/server";

import type { ReadyScope } from "@/features/scope/components";

import type { EditSource } from "@/features/versions/hooks/use-change-write";

import type { SkillRow } from "./skill-rows";

// The page frame hands over a loaded scope in read mode, the skill list and its
// rows are fixed, and the table only hands its link builder to the test.
const READY = {
  files: [],
  edit: null,
  origins: new Map(),
  effective: new Map(),
  inherited: false,
  inheritedFilter: { shown: false, onChange: () => undefined },
  owners: [
    {
      id: "p-1",
      name: "village",
      parentPackId: null,
      currentVersionId: null,
      required: false,
      mountPaths: [],
      repositoryIds: [],
    },
  ],
  release: null,
  scope: { kind: "PACK", packId: "p-1" },
} as unknown as ReadyScope;
const ROW: SkillRow = {
  name: "kazan",
  description: "Kazan tickets",
  directory: ".agents/skills/kazan",
  originName: "org",
  originOwnerId: "p-1",
  paths: null,
  fileCount: 1,
  error: null,
};

const handed: { hrefOf?: (directory: string) => string } = {};
let url = "";

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualScope = { ...(await import("@/features/scope/components")) };
const actualSkills = { ...(await import("../hooks/use-skills")) };
const actualRows = { ...(await import("./skill-rows")) };
const actualNavigation = { ...(await import("next/navigation")) };
const actualTable = { ...(await import("./skills-table")) };
const actualVersions = { ...(await import("@/features/versions/hooks/use-versions")) };
const actualWrite = { ...(await import("@/features/versions/hooks/use-change-write")) };
let source: EditSource | null = { kind: "single", packId: "p-1", current: null };
mock.module("@/features/scope/components", () => ({
  ...actualScope,
  ScopePage: ({ children }: { children: (ready: ReadyScope) => ReactNode }) => children(READY),
}));
mock.module("../hooks/use-skills", () => ({
  ...actualSkills,
  useSkills: () => ({ data: { data: [] }, error: null, refetch: () => undefined }),
}));
mock.module("./skill-rows", () => ({
  ...actualRows,
  serverSkillRows: () => [ROW],
}));
mock.module("next/navigation", () => ({
  ...actualNavigation,
  useRouter: () => ({ push: () => undefined }),
  useSearchParams: () => new URLSearchParams(url),
}));
mock.module("./skills-table", () => ({
  ...actualTable,
  SkillsTable: ({ hrefOf }: { hrefOf: (directory: string) => string }) => {
    handed.hrefOf = hrefOf;
    return <table />;
  },
}));
mock.module("@/features/versions/hooks/use-change-write", () => ({
  ...actualWrite,
  useEditSource: () => source,
  useChangeWrite: () => ({
    run: async () => undefined,
    startChange: async () => undefined,
    isPending: false,
  }),
}));
mock.module("@/features/versions/hooks/use-versions", () => ({
  ...actualVersions,
  useSaveDraftFile: () => ({ mutateAsync: async () => undefined, isPending: false }),
}));
afterAll(() => {
  mock.module("@/features/versions/hooks/use-versions", () => actualVersions);
  mock.module("@/features/versions/hooks/use-change-write", () => actualWrite);
  mock.module("@/features/scope/components", () => actualScope);
  mock.module("../hooks/use-skills", () => actualSkills);
  mock.module("./skill-rows", () => actualRows);
  mock.module("next/navigation", () => actualNavigation);
  mock.module("./skills-table", () => actualTable);
});

const { SkillsPage } = await import("./skills-page");

function render(search: string): string {
  url = search;
  return renderToStaticMarkup(
    <NuqsTestingAdapter searchParams={search}>
      <SkillsPage />
    </NuqsTestingAdapter>
  );
}

describe("SkillsPage", () => {
  beforeEach(() => {
    source = { kind: "single", packId: "p-1", current: null };
  });

  test("a row opens the skill's own page, keeping only the scope, release and inherited setting", () => {
    render("scope=pack%3Ap-1&at=v-0&compare=v-1&view=split");
    expect(handed.hrefOf?.(ROW.directory)).toBe(
      "/skills/.agents%2Fskills%2Fkazan?scope=pack%3Ap-1"
    );
  });

  test("a locked source offers New skill disabled, an editable one enabled", () => {
    source = { kind: "locked", openPackName: "org" };
    expect(render("scope=pack%3Ap-1")).toMatch(
      /<button[^>]*disabled=""[^>]*>[^<]*(<[^>]*>)*New skill/
    );
    source = { kind: "single", packId: "p-1", current: null };
    expect(render("scope=pack%3Ap-1")).not.toMatch(
      /<button[^>]*disabled=""[^>]*>[^<]*(<[^>]*>)*New skill/
    );
  });

  test("a repository's release has the search but no filter and no New skill", () => {
    const mutable = READY as unknown as { inheritedFilter: unknown; scope: unknown };
    const saved = { inheritedFilter: mutable.inheritedFilter, scope: mutable.scope };
    mutable.inheritedFilter = null;
    mutable.scope = { kind: "REPOSITORY", repositoryId: "r-1" };
    try {
      const html = render("");
      expect(html).not.toContain('aria-label="Filter"');
      expect(html).not.toContain("New skill");
      expect(html).toContain('aria-label="Search skills"');
    } finally {
      mutable.inheritedFilter = saved.inheritedFilter;
      mutable.scope = saved.scope;
    }
  });

  test("the toolbar shows the filter, a collapsed search button and New skill, and no count line", () => {
    const html = render("");
    expect(html).not.toContain("1 skill.");
    expect(html).toContain('aria-label="Filter"');
    expect(html).toContain("New skill");
    expect(html).toContain('aria-label="Search skills"');
    expect(html).not.toContain('type="search"');
  });
});
