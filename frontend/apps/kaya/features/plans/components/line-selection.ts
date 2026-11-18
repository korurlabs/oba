/**
 * Selecting a range of a plan's lines in the document gutter, by pointer or
 * keyboard. The anchor is the line the selection started at, the focus the
 * line it reaches to; the range is the two in order. Lines count from 1.
 */

import { PLAN_LIMITS } from "../schemas";

export type LineSelection = { anchor: number; focus: number };

export type LineRange = { start: number; end: number };

/** The selected lines in order. */
export function rangeOf(selection: LineSelection): LineRange {
  return {
    start: Math.min(selection.anchor, selection.focus),
    end: Math.max(selection.anchor, selection.focus),
  };
}

/** A click selects one line; a Shift-click reaches from the current anchor to the clicked line. */
export function selectLine(
  selection: LineSelection | null,
  line: number,
  extend: boolean
): LineSelection {
  if (extend && selection !== null) return { anchor: selection.anchor, focus: line };
  return { anchor: line, focus: line };
}

export type GutterState = {
  selection: LineSelection | null;
  /** The gutter line that holds the keyboard focus. */
  focusLine: number;
};

export type GutterKeyResult = GutterState & {
  /** The comment form should take the focus. */
  openForm: boolean;
};

/**
 * What a key does in the gutter: arrows move the focus, Shift and an arrow
 * extend the selection to the moved focus, Space selects the focused line,
 * Enter opens the comment form on the selection (or on the focused line) and
 * Escape clears it. Null for a key the gutter leaves alone.
 */
export function gutterKey(
  state: GutterState,
  key: string,
  shift: boolean,
  lineCount: number
): GutterKeyResult | null {
  const clamp = (line: number): number => Math.min(Math.max(line, 1), Math.max(lineCount, 1));
  switch (key) {
    case "ArrowDown":
    case "ArrowUp": {
      const focusLine = clamp(state.focusLine + (key === "ArrowDown" ? 1 : -1));
      if (!shift) return { selection: state.selection, focusLine, openForm: false };
      const anchor = state.selection?.anchor ?? state.focusLine;
      return { selection: { anchor, focus: focusLine }, focusLine, openForm: false };
    }
    case " ":
      return {
        selection: selectLine(state.selection, state.focusLine, shift),
        focusLine: state.focusLine,
        openForm: false,
      };
    case "Enter":
      return {
        selection: state.selection ?? { anchor: state.focusLine, focus: state.focusLine },
        focusLine: state.focusLine,
        openForm: true,
      };
    case "Escape":
      return { selection: null, focusLine: state.focusLine, openForm: false };
    default:
      return null;
  }
}

/** The text of the selected lines, cut to the quote limit the server takes. */
export function quoteOf(lines: readonly string[], range: LineRange): string {
  const text = lines.slice(range.start - 1, range.end).join("\n");
  return text.length <= PLAN_LIMITS.quoteMax ? text : text.slice(0, PLAN_LIMITS.quoteMax);
}

/** "Line 4" or "Lines 4 to 9". */
export function rangeText(range: LineRange): string {
  return range.start === range.end ? `Line ${range.start}` : `Lines ${range.start} to ${range.end}`;
}

/** The lines of a version as the document numbers them. */
export function documentLines(content: string): string[] {
  const lines = content.replace(/\r\n/g, "\n").split("\n");
  if (lines.length > 1 && lines.at(-1) === "") lines.pop();
  return lines;
}
