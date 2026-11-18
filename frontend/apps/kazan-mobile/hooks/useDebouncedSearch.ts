import { useEffect, useRef, useState } from "react";

/** The pause after the last keystroke before a search is sent. */
export const SEARCH_DEBOUNCE_MS = 300;

/**
 * The text of a search field that commits after the user pauses typing, so a
 * server search runs once per pause instead of once per keystroke.
 * `committed` is the search in force (a route param); `commit` stores a new
 * one. Returns the field text and its setter.
 */
export function useDebouncedSearch(
  committed: string,
  commit: (text: string) => void,
  delayMs: number = SEARCH_DEBOUNCE_MS
): [string, (text: string) => void] {
  const [text, setText] = useState<string>(committed);
  const commitRef = useRef(commit);

  useEffect(() => {
    commitRef.current = commit;
  }, [commit]);

  useEffect(() => {
    if (text.trim() === committed.trim()) return undefined;
    const timer: ReturnType<typeof setTimeout> = setTimeout(
      () => commitRef.current(text.trim()),
      delayMs
    );
    return () => clearTimeout(timer);
  }, [committed, delayMs, text]);

  return [text, setText];
}
