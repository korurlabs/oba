import { describe, expect, test } from "bun:test";
import type { QueryClient } from "@tanstack/react-query";

import { KayaApiError } from "@/lib/api/errors";

import { refreshAfterRefusal } from "./use-versions";

/** A query client that only records which keys were invalidated. */
function recordingClient(): { client: QueryClient; keys: unknown[] } {
  const keys: unknown[] = [];
  const client = {
    invalidateQueries: async ({ queryKey }: { queryKey: unknown }) => {
      keys.push(queryKey);
    },
  } as unknown as QueryClient;
  return { client, keys };
}

describe("refreshAfterRefusal", () => {
  test("a moved base reloads the owners, so the draft shows as stale with its rebase", async () => {
    const { client, keys } = recordingClient();
    await refreshAfterRefusal(
      client,
      "org-1",
      new KayaApiError("Stale base", 409, "KAY_CFG_002", { conflicts: [] })
    );
    expect(keys).toEqual([
      ["versioned", "org-1", "owners"],
      ["versioned", "org-1", "changes"],
    ]);
  });

  test("a stale override base reloads the effective items, so the dialog shows the parent's current version", async () => {
    const { client, keys } = recordingClient();
    await refreshAfterRefusal(
      client,
      "org-1",
      new KayaApiError("Parent updated again", 409, "KAY_CFG_032", {
        path: ".agents/rules/a.md",
        baseVersionId: "v-4",
        parentVersionId: "v-5",
      })
    );
    expect(keys).toEqual([["versioned", "org-1", "effective"]]);
  });

  test("any other refusal reloads nothing", async () => {
    const { client, keys } = recordingClient();
    await refreshAfterRefusal(client, "org-1", new KayaApiError("Busy", 409, "KAY_CFG_015"));
    await refreshAfterRefusal(client, "org-1", new Error("network"));
    expect(keys).toEqual([]);
  });
});
