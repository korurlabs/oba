import { describe, expect, it } from "bun:test";

import { searchRestoreSteps } from "@/components/boards/selection/selectionSearchModel";

describe("searchRestoreSteps", () => {
  it("reopens Android's search with its text and without the keyboard", () => {
    expect(searchRestoreSteps("android", "login")).toEqual(["focus", "setText", "blur"]);
  });

  it("only fills the iOS field, which stays in the header", () => {
    expect(searchRestoreSteps("ios", "login")).toEqual(["setText"]);
  });

  it("leaves a blank search closed on both platforms", () => {
    expect(searchRestoreSteps("android", "")).toEqual([]);
    expect(searchRestoreSteps("ios", "")).toEqual([]);
    expect(searchRestoreSteps("android", "   ")).toEqual([]);
    expect(searchRestoreSteps("ios", "   ")).toEqual([]);
  });
});
