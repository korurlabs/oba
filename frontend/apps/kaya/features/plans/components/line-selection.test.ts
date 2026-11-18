import { describe, expect, test } from "bun:test";

import {
  documentLines,
  gutterKey,
  quoteOf,
  rangeOf,
  rangeText,
  selectLine,
  type LineSelection,
} from "./line-selection";

describe("gutter selection by pointer", () => {
  test("a click selects one line", () => {
    expect(rangeOf(selectLine(null, 7, false))).toEqual({ start: 7, end: 7 });
  });

  test("a Shift-click reaches from the anchor, upwards too", () => {
    const first = selectLine(null, 40, false);
    expect(rangeOf(selectLine(first, 44, true))).toEqual({ start: 40, end: 44 });
    expect(rangeOf(selectLine(first, 36, true))).toEqual({ start: 36, end: 40 });
  });

  test("a plain click after a range starts over", () => {
    const range: LineSelection = { anchor: 40, focus: 44 };
    expect(rangeOf(selectLine(range, 12, false))).toEqual({ start: 12, end: 12 });
  });

  test("a Shift-click without a selection selects the clicked line", () => {
    expect(rangeOf(selectLine(null, 3, true))).toEqual({ start: 3, end: 3 });
  });
});

describe("gutter selection by keyboard", () => {
  test("Shift and arrows extend the range from the focused line, Enter opens the form", () => {
    let state = { selection: null as LineSelection | null, focusLine: 40 };
    for (let press = 0; press < 4; press += 1) {
      const next = gutterKey(state, "ArrowDown", true, 100);
      if (next === null) throw new Error("ArrowDown is a gutter key");
      state = next;
    }
    const entered = gutterKey(state, "Enter", false, 100);
    expect(entered?.openForm).toBe(true);
    expect(entered?.selection && rangeOf(entered.selection)).toEqual({ start: 40, end: 44 });
  });

  test("arrows alone move the focus and keep the selection", () => {
    const result = gutterKey(
      { selection: { anchor: 2, focus: 2 }, focusLine: 2 },
      "ArrowUp",
      false,
      10
    );
    expect(result).toEqual({ selection: { anchor: 2, focus: 2 }, focusLine: 1, openForm: false });
  });

  test("the focus stays within the document", () => {
    expect(gutterKey({ selection: null, focusLine: 1 }, "ArrowUp", false, 10)?.focusLine).toBe(1);
    expect(gutterKey({ selection: null, focusLine: 10 }, "ArrowDown", true, 10)?.focusLine).toBe(
      10
    );
  });

  test("Enter without a selection anchors the form to the focused line", () => {
    const result = gutterKey({ selection: null, focusLine: 5 }, "Enter", false, 10);
    expect(result?.selection).toEqual({ anchor: 5, focus: 5 });
  });

  test("Space selects the focused line and Escape clears the selection", () => {
    expect(gutterKey({ selection: null, focusLine: 6 }, " ", false, 10)?.selection).toEqual({
      anchor: 6,
      focus: 6,
    });
    expect(
      gutterKey({ selection: { anchor: 1, focus: 3 }, focusLine: 3 }, "Escape", false, 10)
        ?.selection
    ).toBeNull();
  });

  test("other keys are left to the browser", () => {
    expect(gutterKey({ selection: null, focusLine: 1 }, "Tab", false, 10)).toBeNull();
  });
});

describe("quote and labels", () => {
  test("the quote is the selected lines, cut to 500 characters", () => {
    const lines = ["one", "two", "three", "x".repeat(600)];
    expect(quoteOf(lines, { start: 2, end: 3 })).toBe("two\nthree");
    expect(quoteOf(lines, { start: 3, end: 4 })).toHaveLength(500);
  });

  test("a range reads as one line or a span", () => {
    expect(rangeText({ start: 4, end: 4 })).toBe("Line 4");
    expect(rangeText({ start: 40, end: 44 })).toBe("Lines 40 to 44");
  });

  test("a final newline adds no empty line", () => {
    expect(documentLines("a\nb\n")).toEqual(["a", "b"]);
    expect(documentLines("")).toEqual([""]);
  });
});
