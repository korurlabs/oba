import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";

type Call = { method: string; url: string; body?: unknown; config?: unknown };

const calls: Call[] = [];
// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualClient = { ...(await import("@/lib/api/client")) };

const answer = async () => ({ data: { success: true, data: null } });

mock.module("@/lib/api/client", () => ({
  api: {
    get: async (url: string, config?: unknown) => {
      calls.push({ method: "GET", url, config });
      return answer();
    },
    post: async (url: string, body?: unknown) => {
      calls.push({ method: "POST", url, body });
      return answer();
    },
    put: async (url: string, body?: unknown) => {
      calls.push({ method: "PUT", url, body });
      return answer();
    },
    delete: async (url: string, config?: unknown) => {
      calls.push({ method: "DELETE", url, config });
      return answer();
    },
  },
}));

afterAll(() => {
  mock.module("@/lib/api/client", () => actualClient);
});

const versions = await import(".");

beforeEach(() => {
  calls.length = 0;
});

describe("version reads", () => {
  test("a detail, a file by its path and a diff by version", async () => {
    await versions.getVersion("v-1");
    await versions.getVersionFile("v-1", "AGENTS.md");
    await versions.getVersionDiff("v-1");
    await versions.getVersionDiff("v-1", "v-0");
    await versions.getVersionDiff("v-1", "v-0", "AGENTS.md");
    await versions.getVersionDiff("v-1", undefined, "AGENTS.md");
    expect(calls).toEqual([
      { method: "GET", url: "/kaya/versions/v-1", config: undefined },
      { method: "GET", url: "/kaya/versions/v-1/file", config: { params: { path: "AGENTS.md" } } },
      { method: "GET", url: "/kaya/versions/v-1/diff", config: { params: undefined } },
      { method: "GET", url: "/kaya/versions/v-1/diff", config: { params: { against: "v-0" } } },
      {
        method: "GET",
        url: "/kaya/versions/v-1/diff",
        config: { params: { against: "v-0", path: "AGENTS.md" } },
      },
      { method: "GET", url: "/kaya/versions/v-1/diff", config: { params: { path: "AGENTS.md" } } },
    ]);
  });

  test("a restore posts no body", async () => {
    await versions.restoreVersion("v-1");
    expect(calls).toEqual([{ method: "POST", url: "/kaya/versions/v-1/restore", body: undefined }]);
  });
});

describe("draft writes", () => {
  test("create, save, delete a file, rebase and delete", async () => {
    await versions.createDraft({ packId: "p-1" });
    await versions.saveDraftFile("d-1", { path: "AGENTS.md", content: "# A", executable: false });
    await versions.deleteDraftFile("d-1", ".agents/rules/a b.md");
    await versions.rebaseDraft("d-1");
    await versions.rebaseDraft("d-1", { keepDraft: ["AGENTS.md"] });
    await versions.deleteDraft("d-1");
    expect(calls).toEqual([
      { method: "POST", url: "/kaya/drafts", body: { packId: "p-1" } },
      {
        method: "PUT",
        url: "/kaya/drafts/d-1/files",
        body: { path: "AGENTS.md", content: "# A", executable: false },
      },
      {
        method: "DELETE",
        url: "/kaya/drafts/d-1/files",
        config: { params: { path: ".agents/rules/a b.md" } },
      },
      { method: "POST", url: "/kaya/drafts/d-1/rebase", body: {} },
      { method: "POST", url: "/kaya/drafts/d-1/rebase", body: { keepDraft: ["AGENTS.md"] } },
      { method: "DELETE", url: "/kaya/drafts/d-1", config: undefined },
    ]);
  });
});

describe("override writes", () => {
  test("a base move and a cleared record, by item path", async () => {
    await versions.setOverrideBase("d-1", {
      path: ".agents/skills/kazan/",
      baseVersionId: "v-5",
    });
    await versions.clearOverride("d-1", ".agents/rules/a b.md");
    expect(calls).toEqual([
      {
        method: "PUT",
        url: "/kaya/drafts/d-1/overrides",
        body: { path: ".agents/skills/kazan/", baseVersionId: "v-5" },
      },
      {
        method: "DELETE",
        url: "/kaya/drafts/d-1/overrides",
        config: { params: { path: ".agents/rules/a b.md" } },
      },
    ]);
  });
});
