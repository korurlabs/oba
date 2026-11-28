import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { NuqsTestingAdapter } from "nuqs/adapters/testing";
import { renderToStaticMarkup } from "react-dom/server";

import type { ReadyScope } from "@/features/scope/components";
import type { ScopeFile } from "@/features/scope/components/scope-files";

import type { EditSource } from "@/features/versions/hooks/use-change-write";

import type { SkillRow } from "./skill-rows";

// The frame hands over a loaded scope in read mode and fixed skill rows; the
// file's own detail is a stub that names the file it shows.
const KAZAN = ".agents/skills/kazan";
const PLAN = ".agents/skills/plan";

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

function row(directory: string, name: string): SkillRow {
  return {
    name,
    description: `The ${name} skill.`,
    directory,
    originName: "village",
    originOwnerId: "p-1",
    paths: [],
    fileCount: 1,
    error: null,
  };
}

const ROWS = [row(KAZAN, "kazan"), row(PLAN, "plan")];
let files: ScopeFile[] = [];

const actualScope = { ...(await import("@/features/scope/components")) };
const actualNavigation = { ...(await import("next/navigation")) };
const actualSource = { ...(await import("./skills-source")) };
const actualDetail = { ...(await import("@/features/rules/components/file-detail")) };
const actualVersions = { ...(await import("@/features/versions/hooks/use-versions")) };
const actualWrite = { ...(await import("@/features/versions/hooks/use-change-write")) };
let source: EditSource | null = { kind: "single", packId: "p-1", current: null };
mock.module("@/features/scope/components", () => ({
  ...actualScope,
  ScopePage: ({ children }: { children: (ready: ReadyScope) => ReactNode }) =>
    children({
      files,
      edit: null,
      origins: new Map(),
      effective: new Map(),
      inherited: false,
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
    } as unknown as ReadyScope),
}));
mock.module("next/navigation", () => ({
  ...actualNavigation,
  useRouter: () => ({ push: () => undefined, replace: () => undefined }),
  useSearchParams: () => new URLSearchParams("scope=pack%3Ap-1"),
}));
mock.module("./skills-source", () => ({
  ...actualSource,
  SkillsSource: ({ children }: { children: (rows: readonly SkillRow[]) => ReactNode }) =>
    children(ROWS),
}));
mock.module("@/features/rules/components/file-detail", () => ({
  ...actualDetail,
  FileDetail: ({
    file: shown,
    description,
    mainPath,
  }: {
    file: ScopeFile;
    description?: string | null;
    mainPath?: string | null;
  }) => (
    <section
      data-file={shown.path}
      data-description={description ?? undefined}
      data-main-path={mainPath ?? undefined}
    />
  ),
}));
mock.module("@/features/versions/hooks/use-versions", () => ({
  ...actualVersions,
  useVersionFile: () => ({ data: undefined }),
  useDeleteDraftFile: () => ({ mutateAsync: async () => undefined }),
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
afterAll(() => {
  mock.module("@/features/versions/hooks/use-change-write", () => actualWrite);
  mock.module("@/features/scope/components", () => actualScope);
  mock.module("next/navigation", () => actualNavigation);
  mock.module("./skills-source", () => actualSource);
  mock.module("@/features/rules/components/file-detail", () => actualDetail);
  mock.module("@/features/versions/hooks/use-versions", () => actualVersions);
});

const { SkillDetail } = await import("./skill-detail");

function render(directory: string, search = "scope=pack%3Ap-1"): string {
  return renderToStaticMarkup(
    <NuqsTestingAdapter searchParams={search}>
      <SkillDetail directory={directory} />
    </NuqsTestingAdapter>
  );
}

beforeEach(() => {
  source = { kind: "single", packId: "p-1", current: null };
  files = [file(`${KAZAN}/SKILL.md`), file(`${PLAN}/SKILL.md`)];
});

describe("SkillDetail", () => {
  test("a skill's page links back to Skills and names the skill, its pack and folder", () => {
    const html = render(KAZAN);
    expect(html).toContain("Back to Skills");
    expect(html).toContain('href="/skills?scope=pack%3Ap-1"');
    expect(html).toContain('data-slot="page-heading"');
    expect(html).toContain('<h1 class="min-w-0 font-mono text-base break-all">kazan</h1>');
    expect(html).toContain("Pack village");
    expect(html).toContain(KAZAN);
  });

  test("the description is handed to the file's header once, not repeated in the skill's", () => {
    const html = render(KAZAN);
    expect(html).toContain('data-description="The kazan skill."');
    expect(html.split("The kazan skill.")).toHaveLength(2);
  });

  test("the open file shows no path over its header, since the file choice names it", () => {
    files = [...files, file(`${KAZAN}/workflows/sprint.md`)];
    const html = render(KAZAN, `file=${encodeURIComponent(`${KAZAN}/workflows/sprint.md`)}`);
    expect(html).not.toContain("data-main-path");
  });

  test("the arrows lead to the neighbouring skills, are named, and stop at either end", () => {
    const html = render(KAZAN);
    expect(html).toContain('href="/skills/.agents%2Fskills%2Fplan?scope=pack%3Ap-1"');
    expect(html).toContain('aria-label="Next skill: plan"');
    expect(html).toMatch(/<button[^>]*disabled=""[^>]*aria-label="Previous skill"/);
  });

  test("a skill with one file shows that file and no file choice", () => {
    const html = render(KAZAN);
    expect(html).toContain(`data-file="${KAZAN}/SKILL.md"`);
    expect(html).not.toContain("Files of the skill");
  });

  test("a skill with several files offers a choice and shows the one the URL names", () => {
    files = [...files, file(`${KAZAN}/workflows/sprint.md`)];
    const html = render(KAZAN, `file=${encodeURIComponent(`${KAZAN}/workflows/sprint.md`)}`);
    expect(html).toContain('aria-label="Files of the skill"');
    expect(html).toMatch(/aria-pressed="false"[^>]*>SKILL.md<\/button>/);
    expect(html).toContain('<span class="truncate">workflows/sprint.md</span>');
    expect(html).toContain(`data-file="${KAZAN}/workflows/sprint.md"`);
  });

  test("with SKILL.md open, the list button counts the skill's files", () => {
    files = [...files, file(`${KAZAN}/workflows/sprint.md`)];
    const html = render(KAZAN);
    expect(html).toMatch(/aria-pressed="true"[^>]*>SKILL.md<\/button>/);
    expect(html).toContain('<span class="truncate">Files (2)</span>');
  });

  test("a locked source disables Delete skill", () => {
    source = { kind: "locked", reason: "other-change" } as unknown as EditSource;
    expect(render(KAZAN)).toMatch(/<button[^>]*disabled=""[^>]*>Delete skill/);
  });

  test("a folder the scope does not deliver says so", () => {
    const html = render(".agents/skills/gone");
    expect(html).toContain("This scope does not deliver the skill");
    expect(html).toContain("Back to Skills");
  });
});
