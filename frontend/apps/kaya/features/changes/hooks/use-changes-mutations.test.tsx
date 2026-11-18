import { afterAll, beforeAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

import { KayaApiError } from "@/lib/api/errors";

// The mutation hooks run for real; the query library and the API are replaced,
// so the test sees which keys each hook invalidates or removes. The mocks are
// set around this file's tests only, because other test files replace the same
// modules for theirs.
const calls: string[] = [];
let failWith: Error | null = null;
let invalidated: unknown[][] = [];
let removed: unknown[][] = [];

const done = async (name: string) => {
  calls.push(name);
  if (failWith !== null) throw failWith;
  return {};
};

const recordingClient = {
  invalidateQueries: async (filters?: { queryKey?: readonly unknown[] }) => {
    invalidated.push([...(filters?.queryKey ?? [])]);
  },
  removeQueries: (filters?: { queryKey?: readonly unknown[] }) => {
    removed.push([...(filters?.queryKey ?? [])]);
  },
};

type Options = {
  mutationFn: (args: never) => Promise<unknown>;
  onSuccess?: (result: unknown, args: never) => Promise<void> | void;
  onError?: (error: unknown) => Promise<void> | void;
};

/** Runs a mutation as the query library does: the function, then onSuccess or onError. */
function fakeMutation(options: Options) {
  return {
    mutateAsync: async (args: never) => {
      try {
        const result = await options.mutationFn(args);
        await options.onSuccess?.(result, args);
        return result;
      } catch (error: unknown) {
        await options.onError?.(error);
        throw error;
      }
    },
  };
}

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const previous = {
  query: { ...(await import("@tanstack/react-query")) },
  changesApi: { ...(await import("../api")) },
  versionsApi: { ...(await import("@/features/versions/api")) },
  identity: { ...(await import("@/lib/identity/identity-context")) },
};

beforeAll(() => {
  mock.module("@tanstack/react-query", () => ({
    ...previous.query,
    useMutation: fakeMutation,
    useQueryClient: () => recordingClient,
  }));
  mock.module("../api", () => ({
    ...previous.changesApi,
    reviewChange: () => done("review"),
    submitChange: () => done("submit"),
  }));
  mock.module("@/features/versions/api", () => ({
    ...previous.versionsApi,
    restoreVersion: () => done("restore"),
    createDraft: () => done("create"),
    saveDraftFile: () => done("save"),
    deleteDraftFile: () => done("deleteFile"),
    deleteDraft: () => done("deleteDraft"),
    rebaseDraft: () => done("rebase"),
  }));
  mock.module("@/lib/identity/identity-context", () => ({
    ...previous.identity,
    useIdentity: () => ({ organization: { organizationId: "org-1" } }),
  }));
});

afterAll(() => {
  mock.module("@tanstack/react-query", () => previous.query);
  mock.module("../api", () => previous.changesApi);
  mock.module("@/features/versions/api", () => previous.versionsApi);
  mock.module("@/lib/identity/identity-context", () => previous.identity);
});

const { useReviewVersion, useSubmitDraft } = await import("./use-changes");
const {
  useCreateDraft,
  useDeleteDraft,
  useDeleteDraftFile,
  useRebaseDraft,
  useRestoreVersion,
  useSaveDraftFile,
} = await import("@/features/versions/hooks/use-versions");

type Mutations = {
  review: ReturnType<typeof useReviewVersion>;
  restore: ReturnType<typeof useRestoreVersion>;
  create: ReturnType<typeof useCreateDraft>;
  save: ReturnType<typeof useSaveDraftFile>;
  deleteFile: ReturnType<typeof useDeleteDraftFile>;
  deleteDraft: ReturnType<typeof useDeleteDraft>;
  rebase: ReturnType<typeof useRebaseDraft>;
  submit: ReturnType<typeof useSubmitDraft>;
};

let mutations: Mutations;

function Probe({ onRender }: { onRender: (found: Mutations) => void }) {
  onRender({
    review: useReviewVersion(),
    restore: useRestoreVersion(),
    create: useCreateDraft(),
    save: useSaveDraftFile(),
    deleteFile: useDeleteDraftFile(),
    deleteDraft: useDeleteDraft(),
    rebase: useRebaseDraft(),
    submit: useSubmitDraft(),
  });
  return null;
}

beforeEach(() => {
  calls.length = 0;
  failWith = null;
  invalidated = [];
  removed = [];
  const seen: Mutations[] = [];
  renderToStaticMarkup(<Probe onRender={(found) => seen.push(found)} />);
  mutations = seen[0] as Mutations;
});

const DRAFT_KEYS = [
  ["versioned", "org-1", "version", "d-1"],
  ["versioned", "org-1", "file", "d-1"],
  ["versioned", "org-1", "diff", "d-1"],
  ["versioned", "org-1", "kinds"],
  ["versioned", "org-1", "effective"],
  ["versioned", "org-1", "changes"],
];

describe("what each change and draft mutation reloads", () => {
  test("an approval reloads every versioned read of the organization", async () => {
    await mutations.review.mutateAsync({ versionId: "d-1", data: { decision: "APPROVE" } });
    expect(invalidated).toEqual([["versioned", "org-1"]]);
  });

  test("a rejection or a comment reloads only the change itself", async () => {
    await mutations.review.mutateAsync({
      versionId: "d-1",
      data: { decision: "REJECT", comment: "No" },
    });
    expect(invalidated).toEqual(DRAFT_KEYS);
    invalidated.length = 0;
    await mutations.review.mutateAsync({
      versionId: "d-1",
      data: { decision: "COMMENT", comment: "Hm" },
    });
    expect(invalidated).toEqual(DRAFT_KEYS);
  });

  test("a restore and a new draft reload the change lists", async () => {
    await mutations.restore.mutateAsync("v-1");
    expect(invalidated).toEqual([["versioned", "org-1", "changes"]]);
    invalidated.length = 0;
    await mutations.create.mutateAsync({ packId: "p-1" });
    expect(invalidated).toEqual([["versioned", "org-1", "changes"]]);
  });

  test("a file save, a file delete, a rebase and a submit reload the draft's reads", async () => {
    const file = { path: ".agents/rules/a.md", mode: "FILE", content: "x" } as const;
    await mutations.save.mutateAsync({ draftId: "d-1", data: file });
    expect(invalidated).toEqual(DRAFT_KEYS);
    invalidated.length = 0;
    await mutations.deleteFile.mutateAsync({ draftId: "d-1", path: file.path });
    expect(invalidated).toEqual(DRAFT_KEYS);
    invalidated.length = 0;
    await mutations.rebase.mutateAsync({ draftId: "d-1" });
    expect(invalidated).toEqual(DRAFT_KEYS);
    invalidated.length = 0;
    await mutations.submit.mutateAsync({ draftId: "d-1", data: { reason: "Ten characters" } });
    expect(invalidated).toEqual(DRAFT_KEYS);
  });

  test("a deleted draft drops its own entries, so nothing refetches a 404", async () => {
    await mutations.deleteDraft.mutateAsync("d-1");
    expect(removed).toEqual([
      ["versioned", "org-1", "changes", "detail", "d-1"],
      ["versioned", "org-1", "version", "d-1"],
      ["versioned", "org-1", "file", "d-1"],
      ["versioned", "org-1", "diff", "d-1"],
    ]);
    expect(invalidated).toEqual([["versioned", "org-1", "changes"]]);
  });
});

describe("when a mutation is refused", () => {
  const warn = console.warn;

  test("nothing is reloaded for an ordinary refusal, and the error reaches the caller", async () => {
    console.warn = () => undefined;
    try {
      failWith = new KayaApiError("Busy", 409, "KAY_CFG_015");
      await expect(
        mutations.submit.mutateAsync({ draftId: "d-1", data: { reason: "Ten characters" } })
      ).rejects.toBe(failWith);
      expect(invalidated).toEqual([]);
    } finally {
      console.warn = warn;
    }
  });

  test("a moved base (KAY_CFG_002) reloads the owners and the change lists, for any mutation", async () => {
    console.warn = () => undefined;
    try {
      failWith = new KayaApiError("Stale", 409, "KAY_CFG_002", { conflicts: [] });
      await expect(
        mutations.save.mutateAsync({
          draftId: "d-1",
          data: { path: ".agents/rules/a.md", content: "x" },
        })
      ).rejects.toBe(failWith);
      expect(invalidated).toEqual([
        ["versioned", "org-1", "owners"],
        ["versioned", "org-1", "changes"],
      ]);
    } finally {
      console.warn = warn;
    }
  });

  test("the refusal is logged once with its name", async () => {
    const logged: unknown[][] = [];
    console.warn = (...args: unknown[]) => void logged.push(args);
    try {
      failWith = new KayaApiError("Gone", 409, "KAY_CFG_014");
      await mutations.restore.mutateAsync("v-1").catch(() => undefined);
      expect(logged.map((entry) => entry[0])).toEqual(["[versions] Restore refused"]);
    } finally {
      console.warn = warn;
    }
  });
});
