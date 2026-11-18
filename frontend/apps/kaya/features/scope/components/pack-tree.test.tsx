import { afterAll, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";

import type { ReleaseResponse } from "@/features/releases/schemas";
import type { VersionDetailResponse } from "@/features/versions/schemas";

import type { ScopeState } from "../hooks/use-scope";
import type { TreeMark } from "../hooks/use-tree-versions";
import type { Owner } from "../schemas";

// The version and release reads go through the query cache; they are replaced by
// fixed marks. The tooltip shows its content in place, because a static render
// leaves the open tooltip out. The tree is real.
let packMarks = new Map<string, TreeMark<VersionDetailResponse>>();
let releaseMarks = new Map<string, TreeMark<ReleaseResponse>>();

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualTreeVersions = { ...(await import("../hooks/use-tree-versions")) };
const actualTooltip = { ...(await import("@oba/ui/sky/tooltip")) };
mock.module("../hooks/use-tree-versions", () => ({
  ...actualTreeVersions,
  usePackVersions: () => packMarks,
  useNewestReleases: () => releaseMarks,
}));
mock.module("@oba/ui/sky/tooltip", () => ({
  ...actualTooltip,
  TooltipProvider: ({ children }: { children: ReactNode }) => <>{children}</>,
  Tooltip: ({ children }: { children: ReactNode }) => <>{children}</>,
  TooltipTrigger: ({ children }: { children: ReactNode }) => <>{children}</>,
  TooltipContent: ({ children }: { children: ReactNode }) => <i data-tip="">{children}</i>,
}));
afterAll(() => {
  mock.module("../hooks/use-tree-versions", () => actualTreeVersions);
  mock.module("@oba/ui/sky/tooltip", () => actualTooltip);
});

const { PackTree } = await import("./pack-tree");

const version = (number: number, files: number, status = "APPROVED"): VersionDetailResponse =>
  ({
    number,
    status,
    files: Array.from({ length: files }, () => ({})),
  }) as unknown as VersionDetailResponse;
const release = (number: number, fileCount: number): ReleaseResponse =>
  ({ number, createdAt: "2026-10-23T09:00:00Z", fileCount }) as unknown as ReleaseResponse;
const ready = <T,>(value: T | null): TreeMark<T> => ({ status: "ready", value });

const pack = (id: string, name: string, parentPackId: string | null, required = false): Owner => ({
  id,
  name,
  parentPackId,
  currentVersionId: null,
  required,
  mountPaths: [],
  repositoryIds: [],
});
const OWNERS: Owner[] = [
  pack("p-org", "org", null, true),
  pack("p-village", "village", "p-org"),
  pack("p-kagi", "kagi", "p-village"),
  pack("p-acme", "acme", "p-org"),
];

function state(overrides: Partial<ScopeState> = {}): ScopeState {
  return {
    scope: { kind: "PACK", packId: "p-village" },
    options: {
      packs: [],
      repositories: [
        {
          value: "repository:r-1",
          label: "village-repo",
          scope: { kind: "REPOSITORY", repositoryId: "r-1" },
        },
      ],
    },
    owners: OWNERS,
    release: null,
    setScope: () => undefined,
    loading: false,
    error: null,
    retry: () => undefined,
    ...overrides,
  };
}
const render = (value: ScopeState) => renderToStaticMarkup(<PackTree state={value} />);

describe("PackTree", () => {
  test("lists the packs as a tree, parents before their children, every level open", () => {
    const html = render(state());
    const order = ["org", "acme", "village", "kagi"].map((name: string) =>
      html.indexOf(`>${name}</span>`)
    );
    expect(order.every((index: number) => index >= 0)).toBe(true);
    expect(order).toEqual([...order].sort((left: number, right: number) => left - right));
    expect(html).toContain('aria-label="Collapse org"');
    expect(html).toContain('aria-label="Collapse village"');
    expect(html).not.toContain('aria-label="Expand');
    expect(html).not.toContain("Collapse kagi");
  });

  test("marks a required pack and the selected pack", () => {
    const html = render(state());
    expect(html).toContain("Required");
    expect(html).toContain('aria-selected="true"');
    expect(html.match(/aria-selected="true"/g)).toHaveLength(1);
    expect(html.match(/aria-current="true"/g)).toHaveLength(1);
  });

  test("lists the repositories below the packs and marks the selected one", () => {
    const html = render(state({ scope: { kind: "REPOSITORY", repositoryId: "r-1" } }));
    expect(html).toContain('aria-label="Repositories"');
    expect(html).toContain("village-repo");
    expect(html).not.toContain('aria-selected="true"');
    expect(html.match(/aria-current="true"/g)).toHaveLength(1);
  });

  test("without a repository the section is absent; without a pack the tree says so", () => {
    const html = render(
      state({ owners: [], scope: null, options: { packs: [], repositories: [] } })
    );
    expect(html).toContain("No pack yet.");
    expect(html).not.toContain('aria-label="Repositories"');
  });

  test("the tree offers no button to add a pack, whoever views it", () => {
    const html = render(state());
    expect(html).not.toContain("Child pack");
    expect(html).not.toContain(">Pack<");
    expect(html).not.toContain("New pack");
  });

  test("a pack shows v<N> of its current version in grey after the name", () => {
    packMarks = new Map([
      ["p-org", ready(version(7, 3))],
      ["p-village", ready(version(2, 44))],
    ]);
    releaseMarks = new Map();
    const html = render(state());
    expect(html).toMatch(/>org<\/span><span[^>]*text-muted-foreground[^>]*>v7<\/span>/);
    expect(html).toMatch(/>village<\/span><span[^>]*text-muted-foreground[^>]*>v2<\/span>/);
  });

  test("a pack without a version, one still loading and one that failed show no mark", () => {
    packMarks = new Map<string, TreeMark<VersionDetailResponse>>([
      ["p-org", ready(null)],
      ["p-village", { status: "loading" }],
      ["p-kagi", { status: "error" }],
    ]);
    releaseMarks = new Map();
    const html = render(state());
    expect(html).not.toMatch(/>v\d+</);
    expect(html).toContain("No approved version yet.");
    expect(html).toContain("Loading its version.");
    expect(html).toContain("Could not load its version.");
  });

  test("the tooltip of a pack holds its version, status and file count", () => {
    packMarks = new Map([["p-village", ready(version(2, 44))]]);
    releaseMarks = new Map();
    expect(render(state())).toContain("Version 2 (approved), 44 files.");
  });

  test("a repository shows r<N> of its newest release, with the release in its tooltip", () => {
    packMarks = new Map();
    releaseMarks = new Map([["r-1", ready(release(4, 12))]]);
    const html = render(state());
    expect(html).toMatch(/>village-repo<\/span><span[^>]*text-muted-foreground[^>]*>r4<\/span>/);
    expect(html).toContain("Config release 4 of ");
    expect(html).toContain("12 delivered paths.");
  });

  test("a repository without a release shows no mark and says so in its tooltip", () => {
    packMarks = new Map();
    releaseMarks = new Map([["r-1", ready(null)]]);
    const html = render(state());
    expect(html).not.toMatch(/>r\d+</);
    expect(html).toContain("No config release yet.");
  });
});
