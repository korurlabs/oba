"use client";

import {
  useEffect,
  useRef,
  useState,
  type KeyboardEvent,
  type ReactNode,
  type RefObject,
} from "react";

import { cn } from "@/lib/utils";

import {
  documentLines,
  gutterKey,
  quoteOf,
  rangeOf,
  selectLine,
  type LineRange,
  type LineSelection,
} from "./line-selection";

export type PlanDocumentProps = {
  content: string;
  /** Lines that feedback on this version is anchored to. */
  anchoredLines?: ReadonlySet<number>;
  /** The comment form shown below the selected range; it gets the range and its quote. */
  renderForm: (args: {
    range: LineRange;
    quote: string;
    clear: () => void;
    textareaRef: RefObject<HTMLTextAreaElement | null>;
  }) => ReactNode;
};

/**
 * A version as numbered source lines. Clicking a line number selects it,
 * Shift-click reaches a range, and the comment form opens below the range.
 * The gutter also works by keyboard: arrows move, Shift and arrows extend,
 * Enter opens the form, Escape clears. The text is shown as text only.
 * The caller keys it by version, so a selection never outlives its version.
 */
export function PlanDocument({ content, anchoredLines, renderForm }: PlanDocumentProps) {
  const lines = documentLines(content);
  const [selection, setSelection] = useState<LineSelection | null>(null);
  const [focusLine, setFocusLine] = useState(1);
  const container = useRef<HTMLDivElement>(null);
  const textarea = useRef<HTMLTextAreaElement>(null);
  // Enter asks for the form's focus; the textarea takes it once the form has rendered.
  const formFocusWanted = useRef(false);

  useEffect(() => {
    if (formFocusWanted.current && textarea.current !== null) {
      formFocusWanted.current = false;
      textarea.current.focus();
    }
  });

  const gutterButton = (line: number): HTMLButtonElement | null =>
    container.current?.querySelector<HTMLButtonElement>(`[data-line="${line}"]`) ?? null;
  const range = selection === null ? null : rangeOf(selection);
  const lineCount = lines.length;

  const moveFocus = (line: number) => {
    setFocusLine(line);
    gutterButton(line)?.focus();
  };

  const onKeyDown = (event: KeyboardEvent<HTMLButtonElement>) => {
    const result = gutterKey({ selection, focusLine }, event.key, event.shiftKey, lineCount);
    if (result === null) return;
    event.preventDefault();
    setSelection(result.selection);
    if (result.focusLine !== focusLine) moveFocus(result.focusLine);
    if (result.openForm) formFocusWanted.current = true;
  };

  const clear = () => {
    setSelection(null);
    gutterButton(focusLine)?.focus();
  };

  return (
    <div
      ref={container}
      className="overflow-x-auto rounded-md border font-mono text-xs leading-relaxed"
      role="group"
      aria-label="Plan document; select line numbers to comment on them"
    >
      {lines.map((text: string, index: number) => {
        const line = index + 1;
        const selected = range !== null && line >= range.start && line <= range.end;
        const anchored = anchoredLines?.has(line) ?? false;
        return (
          <div key={line}>
            <div className={cn("flex", selected && "bg-info-light")}>
              <button
                type="button"
                data-line={line}
                tabIndex={line === focusLine ? 0 : -1}
                aria-pressed={selected}
                aria-label={`Line ${line}${anchored ? ", has feedback" : ""}`}
                className={cn(
                  "text-muted-foreground hover:text-foreground focus-visible:ring-ring/50 w-14 shrink-0 border-r px-2 text-right tabular-nums outline-none select-none focus-visible:ring-[3px]",
                  anchored && "border-r-warning-text border-r-2 font-semibold"
                )}
                onClick={(event) => {
                  setSelection(selectLine(selection, line, event.shiftKey));
                  setFocusLine(line);
                }}
                onKeyDown={onKeyDown}
              >
                {line}
              </button>
              <span className="min-w-0 flex-1 px-3 break-words whitespace-pre-wrap">
                {text === "" ? " " : text}
              </span>
            </div>
            {range !== null && line === range.end && (
              <div className="bg-muted/40 border-y px-3 py-3 font-sans text-sm">
                {renderForm({
                  range,
                  quote: quoteOf(lines, range),
                  clear,
                  textareaRef: textarea,
                })}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}
