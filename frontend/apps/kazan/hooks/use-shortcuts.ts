"use client";

import { useEffect } from "react";
import { create } from "zustand";

// ── Shortcut Definitions ────────────────────────────────────────────

export type ShortcutAction = "createTicket" | "commandPalette";

interface ShortcutDef {
  /** KeyboardEvent.code (e.g. "KeyN", "KeyK") — immune to Alt/Option key remapping on macOS */
  code: string;
  metaKey?: boolean;
  ctrlKey?: boolean;
  shiftKey?: boolean;
  altKey?: boolean;
  action: ShortcutAction;
}

const SHORTCUTS: ShortcutDef[] = [
  { code: "KeyN", altKey: true, action: "createTicket" },
  { code: "KeyK", metaKey: true, action: "commandPalette" },
];

// ── Store ───────────────────────────────────────────────────────────

interface ShortcutStore {
  activeAction: ShortcutAction | null;
  open: (action: ShortcutAction) => void;
  close: () => void;
  toggle: (action: ShortcutAction) => void;
}

export const useShortcutStore = create<ShortcutStore>((set, get) => ({
  activeAction: null,
  open: (action) => set({ activeAction: action }),
  close: () => set({ activeAction: null }),
  toggle: (action) =>
    set({ activeAction: get().activeAction === action ? null : action }),
}));

// ── Global Listener Hook (mount once in providers) ──────────────────

export function useGlobalShortcuts() {
  const toggle = useShortcutStore((s) => s.toggle);

  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      // Skip when user is typing in an input/textarea/contenteditable
      const target = e.target as HTMLElement;
      if (
        target.isContentEditable ||
        target.tagName === "INPUT" ||
        target.tagName === "TEXTAREA" ||
        target.tagName === "SELECT"
      ) {
        return;
      }

      const meta = e.metaKey || e.ctrlKey;

      for (const shortcut of SHORTCUTS) {
        const codeMatch = e.code === shortcut.code;
        const metaMatch = shortcut.metaKey ? meta : !meta;
        const shiftMatch = shortcut.shiftKey ? e.shiftKey : !e.shiftKey;
        const altMatch = shortcut.altKey ? e.altKey : !e.altKey;

        if (codeMatch && metaMatch && shiftMatch && altMatch) {
          e.preventDefault();
          toggle(shortcut.action);
          return;
        }
      }
    }

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [toggle]);
}

// ── Convenience hooks for consumers ─────────────────────────────────

export function useShortcutAction(action: ShortcutAction) {
  const activeAction = useShortcutStore((s) => s.activeAction);
  const close = useShortcutStore((s) => s.close);
  return {
    isOpen: activeAction === action,
    close,
  };
}
