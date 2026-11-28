"use client";

import * as React from "react";
import { Loader2, Search, X } from "lucide-react";

import { Input } from "./field";
import { cn } from "../src/lib/utils";

/** How long typing pauses before the search is sent, so each key press costs no request. */
export const SEARCH_DELAY_MS = 500;

/** The search an app receives: the trimmed text, or undefined once nothing is left. */
export function normalizeSearch(text: string | null | undefined): string | undefined {
  const trimmed = text?.trim() ?? "";
  return trimmed === "" ? undefined : trimmed;
}

/**
 * The text the field shows once the app passes `incoming` in. When it is only
 * the trimmed echo of what is typed, the raw text stays, so a space the member
 * is still typing is not stripped from under the caret.
 */
export function adoptIncoming(current: string, incoming: string | null | undefined): string {
  const value = incoming ?? "";
  return current.trim() === value ? current : value;
}

export type SearchInputProps = {
  /** The search the app holds, usually from the URL. */
  value?: string | null;
  /** Called after typing pauses, and at once on clear, with the trimmed text or undefined. */
  onSearchChange: (search: string | undefined) => void;
  /** Names the field for assistive technology, such as "Search campaigns". */
  label: string;
  /** Names the clear button for assistive technology, such as "Clear search". */
  clearLabel: string;
  /** Defaults to `label`. */
  placeholder?: string;
  /** Overrides the width the list toolbar expects, `w-40 sm:w-56`. */
  className?: string;
  /** The input element, to put the focus back into it after another control took it. */
  inputRef?: React.Ref<HTMLInputElement>;
  /**
   * Increase it to empty the field. Passing `value` "" again is not enough
   * while a search is still pending, as the field holds the typed text itself.
   */
  resetSignal?: number;
};

/**
 * The search field of a sky list, the first control of its toolbar. It holds
 * the typed text and hands it to the app only after typing pauses, shows a
 * spinner meanwhile, and offers a clear button while it holds text.
 */
export function SearchInput({
  value,
  onSearchChange,
  label,
  clearLabel,
  placeholder,
  className,
  inputRef,
  resetSignal,
}: SearchInputProps) {
  const [text, setText] = React.useState<string>(value ?? "");
  const [debounced, setDebounced] = React.useState<string>(text);
  // The search the app holds, so neither the first render nor the app's echo calls it again.
  const sent = React.useRef<string | undefined>(normalizeSearch(value));
  // The newest callback, without restarting the pause on every render of the app.
  const callback = React.useRef(onSearchChange);
  callback.current = onSearchChange;

  React.useEffect(() => {
    const timer = setTimeout(() => setDebounced(text), SEARCH_DELAY_MS);
    return () => clearTimeout(timer);
  }, [text]);

  React.useEffect(() => {
    const search = normalizeSearch(debounced);
    if (search === sent.current) return;
    sent.current = search;
    callback.current(search);
  }, [debounced]);

  React.useEffect(() => {
    sent.current = normalizeSearch(value);
    setText((current: string) => adoptIncoming(current, value));
  }, [value]);

  // Skipped on mount, so passing a signal never empties the first value.
  const seenSignal = React.useRef(false);
  React.useEffect(() => {
    if (!seenSignal.current) {
      seenSignal.current = true;
      return;
    }
    setText("");
  }, [resetSignal]);

  // The clear button leaves with the text, so the focus moves back into the field.
  const input = React.useRef<HTMLInputElement | null>(null);
  const setInput = React.useCallback(
    (node: HTMLInputElement | null) => {
      input.current = node;
      if (typeof inputRef === "function") inputRef(node);
      else if (inputRef) inputRef.current = node;
    },
    [inputRef]
  );

  const clear = () => {
    input.current?.focus();
    setText("");
    setDebounced("");
    if (sent.current === undefined) return;
    sent.current = undefined;
    callback.current(undefined);
  };

  const pending = text !== debounced;

  return (
    <div data-slot="search-input" className={cn("relative w-40 sm:w-56", className)}>
      <Search
        aria-hidden
        className="text-muted-foreground pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2"
      />
      <Input
        ref={setInput}
        value={text}
        onChange={(event) => setText(event.target.value)}
        aria-label={label}
        placeholder={placeholder ?? label}
        className="pr-8 pl-8"
      />
      {text !== "" &&
        (pending ? (
          <Loader2
            aria-hidden
            className="text-muted-foreground absolute top-1/2 right-2.5 size-4 -translate-y-1/2 animate-spin"
          />
        ) : (
          <button
            type="button"
            aria-label={clearLabel}
            title={clearLabel}
            onClick={clear}
            className="text-muted-foreground hover:text-foreground focus-visible:ring-ring/50 absolute top-1/2 right-1.5 flex size-6 -translate-y-1/2 items-center justify-center rounded-md outline-none focus-visible:ring-[3px]"
          >
            <X aria-hidden className="size-4" />
          </button>
        ))}
    </div>
  );
}
