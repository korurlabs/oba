import { describe, expect, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

import type { ScopeContent } from "@/features/scope/hooks/use-scope-content";
import type { ScopeFile } from "@/features/scope/components/scope-files";

import { PATH_MAPPING_ROWS } from "../schemas/path-mapping-rows.fixture";
import { countedVersions, PathMappingBody, requestedVersionIds } from "./path-mapping-page";

const noop = () => undefined;

function file(path: string, versionId: string): ScopeFile {
  return {
    path,
    mode: "FILE",
    sha256: null,
    size: null,
    target: null,
    alwaysOn: null,
    versionId,
    ownerId: "o-1",
  };
}

describe("PathMappingBody", () => {
  test("a failed load shows the error with a retry", () => {
    const html = renderToStaticMarkup(
      <PathMappingBody
        rows={undefined}
        loading={false}
        error={new Error("Network Error")}
        onRetry={noop}
        scope={null}
        versionCount={0}
      />
    );
    expect(html).toContain('role="alert"');
    expect(html).toContain("Could not load the path mapping");
    expect(html).toContain("Try again");
  });

  test("shows the loading outline while the rows load", () => {
    const html = renderToStaticMarkup(
      <PathMappingBody
        rows={undefined}
        loading
        error={null}
        onRetry={noop}
        scope={null}
        versionCount={0}
      />
    );
    expect(html).toContain('aria-busy="true"');
  });

  test("with no scope the patterns show and the page says how to get counts", () => {
    const html = renderToStaticMarkup(
      <PathMappingBody
        rows={PATH_MAPPING_ROWS}
        loading={false}
        error={null}
        onRetry={noop}
        scope={null}
        versionCount={0}
      />
    );
    expect(html).toContain("Choose a repository or pack");
    expect(html).toContain(".agents/rules/&lt;path&gt;.md");
  });

  test("above 20 versions the page says why the counts are missing", () => {
    const html = renderToStaticMarkup(
      <PathMappingBody
        rows={PATH_MAPPING_ROWS}
        loading={false}
        error={null}
        onRetry={noop}
        scope="repository:r-1"
        versionCount={21}
      />
    );
    expect(html).toContain("more than 20 versions");
  });
});

describe("countedVersions", () => {
  const ready: ScopeContent = {
    status: "ready",
    files: [file("AGENTS.md", "v-1"), file(".agents/rules/a.md", "v-1"), file(".claude/settings.json", "v-2")],
    owners: [],
    release: null,
    newestReleaseNumber: null,
    version: null,
  };

  test("counts every version the scope takes a file from, once", () => {
    expect(countedVersions(false, true, ready)).toEqual({
      status: "ready",
      versionIds: ["v-1", "v-2"],
    });
  });

  test("no scope and an empty scope count no version", () => {
    expect(countedVersions(false, false, { status: "loading" })).toEqual({
      status: "ready",
      versionIds: [],
    });
    expect(countedVersions(false, true, { status: "empty", reason: "no-release" })).toEqual({
      status: "ready",
      versionIds: [],
    });
  });

  test("waits while the scope or its files load, and passes a failed read on", () => {
    expect(countedVersions(true, false, ready).status).toBe("loading");
    expect(countedVersions(false, true, { status: "loading" }).status).toBe("loading");
    const failed = countedVersions(false, true, {
      status: "error",
      error: new Error("x"),
      retry: noop,
    });
    expect(failed.status).toBe("error");
  });
});

describe("requestedVersionIds", () => {
  test("sends no id above the server's limit", () => {
    const many = Array.from({ length: 21 }, (_, index: number) => `v-${index}`);
    expect(requestedVersionIds(many)).toEqual([]);
    expect(requestedVersionIds(many.slice(0, 20))).toHaveLength(20);
  });
});
