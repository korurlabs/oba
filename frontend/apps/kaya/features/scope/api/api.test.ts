import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";

type Call = { url: string; config: unknown };

const calls: Call[] = [];
// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualClient = { ...(await import("@/lib/api/client")) };

mock.module("@/lib/api/client", () => ({
  api: {
    get: async (url: string, config?: unknown) => {
      calls.push({ url, config });
      return { data: { success: true, data: [] } };
    },
    post: async (url: string, body?: unknown) => {
      calls.push({ url, config: body });
      return { data: { success: true, data: null } };
    },
  },
}));

afterAll(() => {
  mock.module("@/lib/api/client", () => actualClient);
});

const scope = await import(".");
const versions = await import("@/features/versions/api");
const releases = await import("@/features/releases/api");
const links = await import("@/features/links/api");
const skills = await import("@/features/skills/api");
const settings = await import("@/features/allowlist/api");

beforeEach(() => {
  calls.length = 0;
});

describe("owner reads", () => {
  test("packs and a repository's pack list", async () => {
    await scope.listPacks();
    await scope.getRepositoryPacks("r/1");
    expect(calls).toEqual([
      { url: "/kaya/packs", config: undefined },
      { url: "/kaya/repositories/r%2F1/packs", config: undefined },
    ]);
  });

  test("a root pack and a child pack are created at /packs", async () => {
    await scope.createPack({ name: "org" });
    await scope.createPack({ name: "kagi", parentPackId: "p-village" });
    expect(calls).toEqual([
      { url: "/kaya/packs", config: { name: "org" } },
      { url: "/kaya/packs", config: { name: "kagi", parentPackId: "p-village" } },
    ]);
  });

  test("a pack's effective items, own only or with the inherited ones, at a version", async () => {
    await scope.getPackEffective("p 1", { inherited: false });
    await scope.getPackEffective("p-1", { versionId: "d-1", inherited: true });
    expect(calls).toEqual([
      { url: "/kaya/packs/p%201/effective", config: { params: { inherited: "false" } } },
      {
        url: "/kaya/packs/p-1/effective",
        config: { params: { inherited: "true", versionId: "d-1" } },
      },
    ]);
  });

  test("returns the envelope the server sent", async () => {
    expect(await scope.listPacks()).toEqual({ success: true, data: [] });
  });
});

describe("content reads", () => {
  test("a version and one of its files, the path as a query param", async () => {
    await versions.getVersion("v 1/2");
    await versions.getVersionFile("v-1", ".agents/rules/a b.md");
    expect(calls).toEqual([
      { url: "/kaya/versions/v%201%2F2", config: undefined },
      { url: "/kaya/versions/v-1/file", config: { params: { path: ".agents/rules/a b.md" } } },
    ]);
  });

  test("a draft is created for a pack", async () => {
    await versions.createDraft({ packId: "p-1" });
    expect(calls).toEqual([{ url: "/kaya/drafts", config: { packId: "p-1" } }]);
  });

  test("releases of a repository, paged, and one by number", async () => {
    await releases.listReleases("r-1", { page: 2, amount: 10 });
    await releases.getRelease("r-1", 7);
    expect(calls).toEqual([
      { url: "/kaya/repositories/r-1/releases", config: { params: { page: 2, amount: 10 } } },
      { url: "/kaya/repositories/r-1/releases/7", config: undefined },
    ]);
  });

  test("links of a repository, with a draft composed in, and of one version", async () => {
    await links.listLinks({ repositoryId: "r-1" });
    await links.listLinks({ repositoryId: "r-1", versionId: "d-1" });
    await links.listLinks({ versionId: "v-1" });
    expect(calls).toEqual([
      { url: "/kaya/links", config: { params: { repositoryId: "r-1" } } },
      { url: "/kaya/links", config: { params: { repositoryId: "r-1", versionId: "d-1" } } },
      { url: "/kaya/links", config: { params: { versionId: "v-1" } } },
    ]);
  });

  test("skills and settings repeat versionId once per version", async () => {
    await skills.listSkills({ versionId: ["v-1", "v-2"] });
    await settings.listSettings({ versionId: ["v-1"] });
    expect(calls).toEqual([
      {
        url: "/kaya/skills",
        config: { params: { versionId: ["v-1", "v-2"] }, paramsSerializer: { indexes: null } },
      },
      {
        url: "/kaya/settings",
        config: { params: { versionId: ["v-1"] }, paramsSerializer: { indexes: null } },
      },
    ]);
  });
});
