import { afterAll, beforeAll, beforeEach, describe, expect, mock, test } from "bun:test";

import type { ScopeFile } from "@/features/scope/components/scope-files";

// The hook runs for real; the query library, the API and the identity are replaced,
// so the test sees the request the hook builds and the options it hands the query
// library. The mocks are set around this file's tests only, because other test
// files replace the same modules for theirs.
type QueryOptions = {
  queryKey: readonly unknown[];
  queryFn: () => Promise<unknown>;
  enabled: boolean;
};

let captured: QueryOptions | null = null;
const requests: unknown[] = [];

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const previous = {
  query: { ...(await import("@tanstack/react-query")) },
  api: { ...(await import("../api")) },
  identity: { ...(await import("@/lib/identity/identity-context")) },
};

beforeAll(() => {
  mock.module("@tanstack/react-query", () => ({
    ...previous.query,
    useQuery: (options: QueryOptions) => {
      captured = options;
      return {};
    },
  }));
  mock.module("../api", () => ({
    ...previous.api,
    getDependencies: async (params: unknown) => {
      requests.push(params);
      return {};
    },
  }));
  mock.module("@/lib/identity/identity-context", () => ({
    ...previous.identity,
    useIdentity: () => ({ organization: { organizationId: "org-1" } }),
  }));
});

afterAll(() => {
  mock.module("@tanstack/react-query", () => previous.query);
  mock.module("../api", () => previous.api);
  mock.module("@/lib/identity/identity-context", () => previous.identity);
});

beforeEach(() => {
  captured = null;
  requests.length = 0;
});

const hooks = await import("./use-dependencies");

function file(path: string, versionId: string): ScopeFile {
  return {
    path,
    mode: "FILE",
    sha256: null,
    size: null,
    target: null,
    alwaysOn: null,
    versionId,
    ownerId: `owner-${versionId}`,
  };
}

describe("useDependencies", () => {
  test("reads one path in the scope's versions, under the kind lists a draft write reloads", async () => {
    hooks.useDependencies(["v-2", "v-1"], "AGENTS.md");
    if (captured === null) throw new Error("the hook did not call useQuery");
    const options = captured as QueryOptions;
    await options.queryFn();
    expect(requests).toEqual([{ versionId: ["v-2", "v-1"], path: "AGENTS.md" }]);
    expect(options.enabled).toBe(true);
    expect(options.queryKey.slice(0, 3)).toEqual(["versioned", "org-1", "kinds"]);
  });

  test("nothing is read without a version or a path", () => {
    hooks.useDependencies([], "AGENTS.md");
    expect((captured as QueryOptions | null)?.enabled).toBe(false);
    hooks.useDependencies(["v-1"], "");
    expect((captured as QueryOptions | null)?.enabled).toBe(false);
  });
});

describe("dependencyVersionIds", () => {
  test("the open file's version comes first, then each other version once", () => {
    const files = [file("a.md", "v-1"), file("b.md", "v-2"), file("c.md", "v-1")];
    expect(hooks.dependencyVersionIds(files, file("b.md", "v-2"))).toEqual(["v-2", "v-1"]);
  });

  test("a scope of more versions than one read takes is cut at 20, the open file's kept", () => {
    const files = Array.from({ length: 25 }, (_, index: number) =>
      file(`${index}.md`, `v-${index}`)
    );
    const ids = hooks.dependencyVersionIds(files, file("24.md", "v-24"));
    expect(ids).toHaveLength(20);
    expect(ids[0]).toBe("v-24");
  });
});
