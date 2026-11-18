import { describe, expect, test } from "bun:test";
import { QueryClient } from "@tanstack/react-query";

import { contiguousSeq, deletedSessionFilters, loadNewerEvents } from "./use-session-detail";

const event = (seq: number) => ({ seq, event: "tool", at: `t${seq}` });

describe("contiguousSeq", () => {
  test("counts the events from seq 1 up to the first gap", () => {
    expect(contiguousSeq([])).toBe(0);
    expect(contiguousSeq([event(1), event(2), event(3)])).toBe(3);
    expect(contiguousSeq([event(1), event(2), event(4)])).toBe(2);
    expect(contiguousSeq([event(2), event(3)])).toBe(0);
    expect(contiguousSeq([{ event: "no seq" }])).toBe(0);
  });
});

describe("loadNewerEvents", () => {
  test("asks for the events after the contiguous seq and appends them", async () => {
    const asked: number[] = [];
    const result = await loadNewerEvents("s1", [event(1), event(2), event(4)], async (_, after) => {
      asked.push(after ?? 0);
      return { success: true, data: [event(3), event(4), event(5)] };
    });
    expect(asked).toEqual([2]);
    expect(result.data).toEqual([event(1), event(2), event(3), event(4), event(5)]);
  });

  test("a first load asks for everything", async () => {
    const asked: number[] = [];
    const result = await loadNewerEvents("s1", [], async (_, after) => {
      asked.push(after ?? 0);
      return { success: true, data: [event(1)] };
    });
    expect(asked).toEqual([0]);
    expect(result.data).toEqual([event(1)]);
  });

  test("an answer without data keeps the known events", async () => {
    const result = await loadNewerEvents("s1", [event(1)], async () => ({
      success: true,
      data: null,
    }));
    expect(result.data).toEqual([event(1)]);
  });
});

describe("deletedSessionFilters", () => {
  test("find only a finished delete of that session in that organization", async () => {
    const queryClient = new QueryClient();
    const run = async (key: readonly string[], sessionId: string, fail = false) => {
      const mutation = queryClient.getMutationCache().build(queryClient, {
        mutationKey: key,
        mutationFn: async () => {
          if (fail) throw new Error("refused");
        },
      });
      await mutation.execute(sessionId).catch((error: unknown) => {
        // The refused delete is part of the setup: it must not count as deleted.
        expect((error as Error).message).toBe("refused");
      });
    };
    await run(["sessions", "org-1", "delete"], "s1");
    await run(["sessions", "org-1", "delete"], "s2", true);
    await run(["sessions", "org-2", "delete"], "s3");

    const found = (organizationId: string, sessionId: string) =>
      queryClient.getMutationCache().findAll(deletedSessionFilters(organizationId, sessionId))
        .length;
    expect(found("org-1", "s1")).toBe(1);
    expect(found("org-1", "s2")).toBe(0);
    expect(found("org-1", "s3")).toBe(0);
    expect(found("org-2", "s3")).toBe(1);
  });
});
