import { afterAll, describe, expect, mock, test } from "bun:test";

const calls: { url: string; body: unknown }[] = [];
// A copy, not the namespace: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mock back for the files that run later.
const actualClient = { ...(await import("@/lib/api/client")) };

mock.module("@/lib/api/client", () => ({
  api: {
    put: async (url: string, body?: unknown) => {
      calls.push({ url, body });
      return { data: { success: true, data: null } };
    },
  },
}));

afterAll(() => {
  mock.module("@/lib/api/client", () => actualClient);
});

const repositories = await import(".");

describe("repository packs", () => {
  test("the whole list is put with each pack's folder", async () => {
    await repositories.updateRepositoryPacks("r/1", {
      packs: [
        { packId: "p-village", rootPath: null },
        { packId: "p-kagi", rootPath: "projects/kagi" },
      ],
    });
    expect(calls).toEqual([
      {
        url: "/kaya/repositories/r%2F1/packs",
        body: {
          packs: [
            { packId: "p-village", rootPath: null },
            { packId: "p-kagi", rootPath: "projects/kagi" },
          ],
        },
      },
    ]);
  });
});
