import { describe, expect, test } from "bun:test";
import { compareLines, MAX_COMPARE_CELLS, stripBlobSignatures } from "./line-diff";

describe("compareLines", () => {
  test("keeps equal texts as same rows", () => {
    expect(compareLines("a\nb", "a\nb")).toEqual([
      { kind: "same", left: "a", right: "a" },
      { kind: "same", left: "b", right: "b" },
    ]);
  });

  test("pairs a replaced line and marks added and removed lines", () => {
    expect(compareLines("# Title\nold\nkeep\ngone", "# Title\nnew\nkeep\nextra\nmore")).toEqual([
      { kind: "same", left: "# Title", right: "# Title" },
      { kind: "changed", left: "old", right: "new" },
      { kind: "same", left: "keep", right: "keep" },
      { kind: "changed", left: "gone", right: "extra" },
      { kind: "added", left: null, right: "more" },
    ]);
  });

  test("treats an empty text as having no lines", () => {
    expect(compareLines("", "a")).toEqual([{ kind: "added", left: null, right: "a" }]);
    expect(compareLines("a", "")).toEqual([{ kind: "removed", left: "a", right: null }]);
    expect(compareLines("", "")).toEqual([]);
  });

  test("ignores Windows line endings", () => {
    expect(compareLines("a\r\nb", "a\nb")?.every((row) => row.kind === "same")).toBe(true);
  });

  test("gives up on texts whose compare table would be too large", () => {
    const lines = Math.ceil(Math.sqrt(MAX_COMPARE_CELLS)) + 1;
    const older = Array.from({ length: lines }, (_, index) => `old ${index}`).join("\n");
    const newer = Array.from({ length: lines }, (_, index) => `new ${index}`).join("\n");
    expect(compareLines(older, newer)).toBeNull();
  });

  test("compares a small edit in a long text through the shared head and tail", () => {
    const base = Array.from({ length: 5000 }, (_, index) => `line ${index}`);
    const edited = [...base];
    edited[2500] = "edited";
    const rows = compareLines(base.join("\n"), edited.join("\n"));
    expect(rows).not.toBeNull();
    expect(rows!.filter((row) => row.kind !== "same")).toEqual([
      { kind: "changed", left: "line 2500", right: "edited" },
    ]);
  });
});

describe("stripBlobSignatures", () => {
  const blob = "https://korur.blob.core.windows.net/wiki/pages/1/scope.pdf";
  const signedAt = (start: string, expiry: string) =>
    `${blob}?sv=2024-11-04&st=${start}&se=${expiry}&sr=b&sp=r&sig=abc%2Bdef%3D`;

  test("keeps a line with a link equal when the two copies are signed separately", () => {
    const older = `# Scope\nSee [scope.pdf](${signedAt("2026-11-23T10%3A00%3A00Z", "2026-11-23T11%3A00%3A00Z")}).`;
    const newer = `# Scope\nSee [scope.pdf](${signedAt("2026-11-23T10%3A05%3A00Z", "2026-11-23T11%3A05%3A00Z")}).`;

    expect(compareLines(older, newer)?.some((row) => row.kind !== "same")).toBe(true);
    expect(
      compareLines(stripBlobSignatures(older), stripBlobSignatures(newer))?.every(
        (row) => row.kind === "same"
      )
    ).toBe(true);
    expect(stripBlobSignatures(newer)).toBe(`# Scope\nSee [scope.pdf](${blob}).`);
  });

  test("leaves URLs without a signature untouched", () => {
    const text = "[a](https://example.com/search?q=sig&page=2) and https://example.com/x?design=1";
    expect(stripBlobSignatures(text)).toBe(text);
  });
});
