import { describe, expect, test } from "bun:test";

import type { PackResponse, RepositoryPackEntryResponse } from "@/features/scope/schemas";
import { KayaApiError } from "@/lib/api/errors";

import {
  checkRows,
  packOptions,
  refusedRowKey,
  requiredEntries,
  rowsOf,
  rowsWith,
  type PackRow,
} from "./repository-pack-rows";

function pack(id: string, parentPackId: string | null = null, required = false): PackResponse {
  return {
    id,
    name: id,
    parentPackId,
    required,
    mountPaths: [],
    createdAt: "2026-10-28T00:00:00Z",
  };
}

function entry(id: string, rootPath: string | null, required = false): RepositoryPackEntryResponse {
  return { id, name: id, required, rootPath, createdAt: "2026-10-28T00:00:00Z" };
}

function row(key: string, packId: string, folder = ""): PackRow {
  return { key, packId, folder };
}

describe("rows of a repository's pack list", () => {
  test("required packs stay read only; the own packs become rows with their folder", () => {
    const entries = [
      entry("guard", null, true),
      entry("village", null),
      entry("kagi", "projects/kagi"),
    ];
    expect(requiredEntries(entries).map((e) => e.id)).toEqual(["guard"]);
    expect(rowsOf(entries)).toEqual([
      row("village", "village", ""),
      row("kagi", "kagi", "projects/kagi"),
    ]);
  });

  test("a row offers the tree in order without required packs or those of other rows", () => {
    const packs = [
      pack("org"),
      pack("village", "org"),
      pack("kagi", "village"),
      pack("guard", null, true),
    ];
    const rows = [row("a", "village"), row("b", "")];
    expect(packOptions(packs, rows, "b")).toEqual([
      { id: "org", name: "org", depth: 0 },
      { id: "kagi", name: "kagi", depth: 2 },
    ]);
    expect(packOptions(packs, rows, "a").map((option) => option.id)).toEqual([
      "org",
      "village",
      "kagi",
    ]);
  });
});

describe("checkRows", () => {
  test("an empty folder is the repository root, a folder mounts the pack", () => {
    expect(checkRows([row("a", "village"), row("b", "kagi", " projects/kagi ")])).toEqual({
      ok: true,
      data: {
        packs: [
          { packId: "village", rootPath: null },
          { packId: "kagi", rootPath: "projects/kagi" },
        ],
      },
    });
  });

  test("a row without a pack is named", () => {
    expect(checkRows([row("a", "village"), row("b", "")])).toMatchObject({
      ok: false,
      rowKey: "b",
    });
  });

  test("an invalid or reserved folder is refused on its row", () => {
    expect(checkRows([row("a", "kagi", "../kagi")])).toMatchObject({ ok: false, rowKey: "a" });
    const reserved = checkRows([row("a", "kagi", ".agents/kagi")]);
    expect(reserved).toMatchObject({ ok: false, rowKey: "a" });
    expect(checkRows([row("a", "kagi", "docs/kagi")])).toMatchObject({
      ok: false,
      message:
        "The folder cannot start with .agents, .claude or docs, the folders of the delivered config.",
    });
  });

  test("a folder given twice or inside another is refused on the later row", () => {
    expect(
      checkRows([row("a", "kagi", "projects/kagi"), row("b", "kazan", "projects/kagi")])
    ).toEqual({
      ok: false,
      message: "The folder projects/kagi is given twice.",
      rowKey: "b",
    });
    expect(checkRows([row("a", "kagi", "projects"), row("b", "kazan", "projects/kazan")])).toEqual({
      ok: false,
      message: "The folders projects and projects/kazan lie inside one another.",
      rowKey: "b",
    });
    expect(
      checkRows([row("a", "kagi", "projects/kagi"), row("b", "kazan", "projects/kagi2")])
    ).toMatchObject({
      ok: true,
    });
  });
});

describe("refusedRowKey", () => {
  const rows = [
    row("a", "village"),
    row("b", "kagi", "projects/kagi"),
    row("c", "kazan", "projects/kazan"),
  ];

  test("KAY_CFG_034 marks the row of the rule's folder", () => {
    const error = new KayaApiError("refused", 422, "KAY_CFG_034", {
      rule: ".agents/rules/kagi/api.md",
      folder: "projects/kagi",
      pack: "kagi",
    });
    expect(refusedRowKey(error, rows)).toBe("b");
  });

  test("KAY_CFG_026 marks the row whose folder its reason names", () => {
    const error = new KayaApiError("refused", 422, "KAY_CFG_026", {
      rootPath: "The folder projects/kazan lies inside or around projects",
    });
    expect(refusedRowKey(error, rows)).toBe("c");
  });

  test("other refusals mark no row", () => {
    expect(refusedRowKey(new KayaApiError("refused", 409, "KAY_CFG_033", {}), rows)).toBeNull();
    expect(refusedRowKey(new Error("offline"), rows)).toBeNull();
  });
});

describe("rowsWith", () => {
  const entries = [
    entry("guard", null, true),
    entry("village", null),
    entry("kagi", "projects/kagi"),
  ];

  test("adds the pack at the end at the repository root and drops the required packs", () => {
    expect(rowsWith(entries, "docs")).toEqual([
      row("village", "village"),
      row("kagi", "kagi", "projects/kagi"),
      row("add-docs", "docs"),
    ]);
  });

  test("a pack the list holds, own or required, is not added again", () => {
    expect(rowsWith(entries, "kagi")).toEqual(rowsOf(entries));
    expect(rowsWith(entries, "guard")).toEqual(rowsOf(entries));
  });
});
