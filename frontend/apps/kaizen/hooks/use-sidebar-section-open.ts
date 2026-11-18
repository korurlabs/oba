"use client";

import { useCallback, useEffect, useState } from "react";

/** The remembered open state of a section, or null when none is stored or storage is unavailable. */
function readSectionOpen(storageKey: string): boolean | null {
  try {
    const stored = window.localStorage.getItem(storageKey);
    if (stored === "true") return true;
    if (stored === "false") return false;
    // eslint-disable-next-line no-empty -- reason: the open state is a per-viewer convenience; the section falls back to open and keeps working
  } catch {}
  return null;
}

function writeSectionOpen(storageKey: string, open: boolean): void {
  try {
    window.localStorage.setItem(storageKey, String(open));
    // eslint-disable-next-line no-empty -- reason: the open state is a per-viewer convenience; the section still opens and closes, it is only not remembered
  } catch {}
}

/**
 * Whether a sidebar accordion section is open, remembered in this browser
 * under `storageKey`. It starts open. Storage is read after mount, because the
 * server render has no access to it, so the first client render matches the
 * server's open section.
 */
export function useSidebarSectionOpen(storageKey: string): [boolean, (open: boolean) => void] {
  const [open, setOpen] = useState(true);

  useEffect(() => {
    const stored = readSectionOpen(storageKey);
    if (stored !== null) setOpen(stored);
  }, [storageKey]);

  const changeOpen = useCallback(
    (next: boolean) => {
      setOpen(next);
      writeSectionOpen(storageKey, next);
    },
    [storageKey]
  );

  return [open, changeOpen];
}
