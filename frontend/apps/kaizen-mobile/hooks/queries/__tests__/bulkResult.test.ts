import { describe, expect, it } from "bun:test";

import { partitionSettled } from "@/hooks/queries/bulkResult";

describe("partitionSettled", () => {
  it("splits successes and failures by target, keeping the failure message", async () => {
    const targets: string[] = ["a", "b", "c"];
    const outcomes = await Promise.allSettled([
      Promise.resolve(1),
      Promise.reject(new Error("forbidden")),
      Promise.reject("offline"),
    ]);
    expect(partitionSettled(targets, outcomes)).toEqual({
      succeeded: ["a"],
      failed: [
        { target: "b", message: "forbidden" },
        { target: "c", message: "offline" },
      ],
    });
  });

  it("returns empty lists for no targets", () => {
    expect(partitionSettled([], [])).toEqual({ succeeded: [], failed: [] });
  });
});
