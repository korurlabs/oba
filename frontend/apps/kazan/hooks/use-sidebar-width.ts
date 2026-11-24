"use client";

import { useCallback, useEffect, useState } from "react";
import { SIDEBAR_WIDTH } from "@oba/ui/sky/sidebar";

/** Narrowest expanded sidebar: a root board name still shows about 68px of text. */
export const SIDEBAR_WIDTH_MIN_PX = 224;
/** Widest sidebar a viewer can drag to. */
export const SIDEBAR_WIDTH_MAX_PX = 320;
/** Pixels in one rem: the sky theme keeps the browser's default root font size. */
const PX_PER_REM = 16;
/**
 * Width a viewer gets before choosing one: the shared sky sidebar width, so
 * Kazan opens as wide as Kaya and the other apps.
 */
export const SIDEBAR_WIDTH_DEFAULT_PX = Number.parseFloat(SIDEBAR_WIDTH) * PX_PER_REM;
/** One arrow-key press on the resize handle changes the width by this much. */
export const SIDEBAR_WIDTH_KEY_STEP_PX = 16;

const SIDEBAR_WIDTH_STORAGE_KEY = "kazan-sidebar-width";

export function clampSidebarWidth(width: number): number {
  return Math.min(SIDEBAR_WIDTH_MAX_PX, Math.max(SIDEBAR_WIDTH_MIN_PX, Math.round(width)));
}

function readStoredWidth(): number | null {
  try {
    const stored = window.localStorage.getItem(SIDEBAR_WIDTH_STORAGE_KEY);
    if (stored === null || stored.trim() === "") return null;
    const parsed = Number(stored);
    return Number.isFinite(parsed) ? clampSidebarWidth(parsed) : null;
    // eslint-disable-next-line no-empty -- reason: the sidebar width is a per-viewer convenience; the sidebar keeps the default or current width and keeps working
  } catch {}
  return null;
}

function writeStoredWidth(width: number): void {
  try {
    window.localStorage.setItem(SIDEBAR_WIDTH_STORAGE_KEY, String(width));
    // eslint-disable-next-line no-empty -- reason: the sidebar width is a per-viewer convenience; the sidebar keeps the default or current width and keeps working
  } catch {}
}

/**
 * Width of the expanded desktop sidebar in px: the viewer's own choice,
 * remembered in this browser only, or the shared default. Storage is read after
 * mount, because the server render has no access to it; `isRestored` turns
 * true in the render that applies the stored width. A stored value that is
 * not a number falls back to the default, one out of range is clamped. When
 * storage is unavailable a chosen width lasts until the page is left.
 *
 * `preview` changes the width without remembering it (every pointer move of a
 * drag); `commit` changes and remembers it.
 */
export function useSidebarWidth(): {
  width: number;
  isRestored: boolean;
  preview: (width: number) => void;
  commit: (width: number) => void;
} {
  const [width, setWidth] = useState(SIDEBAR_WIDTH_DEFAULT_PX);
  const [isRestored, setIsRestored] = useState(false);

  useEffect(() => {
    const storedWidth = readStoredWidth();
    if (storedWidth !== null) setWidth(storedWidth);
    setIsRestored(true);
  }, []);

  const preview = useCallback((next: number) => setWidth(clampSidebarWidth(next)), []);

  const commit = useCallback((next: number) => {
    const clamped = clampSidebarWidth(next);
    setWidth(clamped);
    writeStoredWidth(clamped);
  }, []);

  return { width, isRestored, preview, commit };
}
