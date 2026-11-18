import { describe, expect, it } from "bun:test";

import {
  boardPickRows,
  selectAllIds,
  type BoardPickItem,
} from "@/components/boards/boardPickModel";

function item(id: string, name: string, prefix: string | null, disabled = false): BoardPickItem {
  return {
    id,
    name,
    prefix,
    color: null,
    indent: 0,
    disabled,
    note: disabled ? "Admin role needed" : null,
    noteTone: "muted",
  };
}

const ITEMS: BoardPickItem[] = [
  item("a", "Alpha", "ALP"),
  item("w", "Webshop", "WEB", true),
  item("m", "Mobile", null),
  item("d", "Design", "DSN"),
];

describe("boardPickRows in multi mode", () => {
  it("ticks every picked board and keeps the list order", () => {
    expect(
      boardPickRows(ITEMS, "multi", ["d", "a"], "").map((row) => [row.item.id, row.checked])
    ).toEqual([
      ["a", true],
      ["w", false],
      ["m", false],
      ["d", true],
    ]);
  });

  it("searches the name or the prefix, ignoring case and spaces around", () => {
    expect(boardPickRows(ITEMS, "multi", [], "  web ").map((row) => row.item.id)).toEqual(["w"]);
    expect(boardPickRows(ITEMS, "multi", [], "dsn").map((row) => row.item.id)).toEqual(["d"]);
    expect(boardPickRows(ITEMS, "multi", [], "o").map((row) => row.item.id)).toEqual(["w", "m"]);
    expect(boardPickRows(ITEMS, "multi", [], "nothing")).toEqual([]);
  });

  it("keeps a picked board ticked while the search hides others", () => {
    expect(boardPickRows(ITEMS, "multi", ["m"], "mob")).toEqual([
      { item: ITEMS[2]!, checked: true },
    ]);
  });
});

describe("boardPickRows in single mode", () => {
  it("ticks only the picked board", () => {
    expect(
      boardPickRows(ITEMS, "single", ["m"], "")
        .filter((row) => row.checked)
        .map((row) => row.item.id)
    ).toEqual(["m"]);
  });

  it("ticks nothing before a pick and only the first id when given several", () => {
    expect(boardPickRows(ITEMS, "single", [], "").some((row) => row.checked)).toBe(false);
    expect(
      boardPickRows(ITEMS, "single", ["d", "a"], "")
        .filter((row) => row.checked)
        .map((row) => row.item.id)
    ).toEqual(["d"]);
  });
});

describe("selectAllIds", () => {
  it("ticks every board that can be picked, in list order", () => {
    expect(selectAllIds(ITEMS)).toEqual(["a", "m", "d"]);
  });
});
