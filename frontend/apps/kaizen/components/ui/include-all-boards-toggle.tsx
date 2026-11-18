"use client";

import { useState, useCallback } from "react";

const STORAGE_KEY = "sprint-include-all-boards";

/**
 * Whether a board list includes tickets from its child boards, remembered in
 * localStorage across visits.
 *
 * The control that writes it is the "include all boards" switch inside the
 * toolbars' View popover (ViewOptionsPopover); this file no longer ships a
 * toolbar control of its own, because the bare icon + switch that used to sit
 * on the toolbar was routinely misread as a list/tree view toggle.
 */
export function useIncludeAllBoards(defaultValue = true) {
  const [includeAllBoards, setIncludeAllBoardsState] = useState(() => {
    if (typeof window === "undefined") return defaultValue;
    const stored = window.localStorage.getItem(STORAGE_KEY);
    return stored !== null ? stored === "true" : defaultValue;
  });

  const setIncludeAllBoards = useCallback((value: boolean) => {
    setIncludeAllBoardsState(value);
    window.localStorage.setItem(STORAGE_KEY, String(value));
  }, []);

  return [includeAllBoards, setIncludeAllBoards] as const;
}
