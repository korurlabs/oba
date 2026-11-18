"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  useSyncExternalStore,
  type MouseEvent,
  type ReactNode,
} from "react";

import type { DraftSession } from "../hooks/use-draft-session";
import type { FileMode, VersionDetailResponse } from "../schemas";
import type { Owner } from "@/features/scope/schemas";

/** The draft a page edits, and the pack it changes. */
export type DraftEdit = {
  draft: VersionDetailResponse;
  target: Owner;
  session: DraftSession;
};

/**
 * The text open in an editor and not yet saved. `text` is null when the
 * editor holds no text: a symlink's target, a binary file's new path, or
 * allowlist rules not yet saved. `replaces` is the file's saved path when the editor
 * renames it. `dirty` says the editor holds a change the draft does not have
 * yet, which leaving the editor would lose.
 */
export type LiveEdit = {
  path: string;
  mode: FileMode;
  text: string | null;
  replaces: string | null;
  dirty: boolean;
  /** The file the edit started from; absent when the edit is not one file's text, such as allowlist rule rows. */
  origin?: EditOrigin;
};

/**
 * The file an editor's edit started from: an existing file's saved path and
 * the hash of the content the editor loaded, or a new file's path with
 * `isNew`, which has no saved content. A change that takes the editor over
 * must still hold that content, or the typed text would be lost or overwrite
 * what the change has.
 */
export type EditOrigin = {
  path: string;
  sha256: string | null;
  isNew: boolean;
};

/**
 * Why a change cannot be submitted or started while an editor holds unsaved
 * text: a submit would leave the text out, and a start would move the editor
 * onto another change.
 */
export const UNSAVED_EDIT_BLOCKER = "Save or discard your open edit first.";

export const DISCARD_EDIT_QUESTION = "Your open edit is not saved. Discard it?";

/**
 * Whether the member may leave the open editor: always when it holds nothing
 * unsaved, else only when they agree to discard the edit.
 */
export function mayLeaveEdit(
  live: LiveEdit | null,
  confirm: (question: string) => boolean
): boolean {
  return !live?.dirty || confirm(DISCARD_EDIT_QUESTION);
}

/** Asks the browser to confirm a reload or a closed tab, as long as an edit is unsaved. */
export function guardUnload(event: BeforeUnloadEvent): void {
  event.preventDefault();
  // Older browsers show the prompt only when returnValue is set.
  event.returnValue = "";
}

/**
 * The unsaved edits of the mounted pages and the files they started from,
 * for controls outside a page's provider, such as the sidebar and the top
 * bar, that cannot read its context.
 */
const unsavedEdits = new Map<symbol, EditOrigin | null>();

/** Controls outside the page, such as the top bar, re-render when an edit turns unsaved or is released. */
const unsavedEditListeners = new Set<() => void>();

function notifyUnsavedEdits(): void {
  unsavedEditListeners.forEach((listener: () => void) => listener());
}

/** Marks an unsaved edit as open until the returned release is called. */
export function holdUnsavedEdit(origin: EditOrigin | null = null): () => void {
  const hold = Symbol("unsaved edit");
  unsavedEdits.set(hold, origin);
  notifyUnsavedEdits();
  return () => {
    unsavedEdits.delete(hold);
    notifyUnsavedEdits();
  };
}

/** Whether a mounted page holds an edit that is not saved. */
export function hasUnsavedEdit(): boolean {
  return unsavedEdits.size > 0;
}

/** The file an open unsaved edit started from; null when none is open or it is not one file's text. */
export function unsavedEditOrigin(): EditOrigin | null {
  for (const origin of unsavedEdits.values()) {
    if (origin !== null) return origin;
  }
  return null;
}

function subscribeUnsavedEdits(listener: () => void): () => void {
  unsavedEditListeners.add(listener);
  return () => {
    unsavedEditListeners.delete(listener);
  };
}

/** `hasUnsavedEdit` for a control outside the page, kept current as edits come and go. */
export function useHasUnsavedEdit(): boolean {
  return useSyncExternalStore(subscribeUnsavedEdits, hasUnsavedEdit, () => false);
}

/**
 * Whether the member may leave the page from outside its editor: always when
 * no edit is unsaved, else only when they agree to discard it.
 */
export function mayLeavePage(confirm: (question: string) => boolean): boolean {
  return unsavedEdits.size === 0 || confirm(DISCARD_EDIT_QUESTION);
}

type LiveEditState = {
  live: LiveEdit | null;
  setLive: (live: LiveEdit | null) => void;
};

const LiveEditContext = createContext<LiveEditState>({
  live: null,
  setLive: () => undefined,
});

export function LiveEditProvider({ children }: { children: ReactNode }) {
  const [live, setLive] = useState<LiveEdit | null>(null);
  const value = useMemo(() => ({ live, setLive }), [live]);
  const dirty = live?.dirty ?? false;
  // Held by value, so typing in the editor does not release and hold the edit again.
  const originPath = live?.origin?.path ?? null;
  const originSha256 = live?.origin?.sha256 ?? null;
  const originIsNew = live?.origin?.isNew ?? false;
  useEffect(() => {
    if (!dirty) return;
    const release = holdUnsavedEdit(
      originPath === null ? null : { path: originPath, sha256: originSha256, isNew: originIsNew }
    );
    window.addEventListener("beforeunload", guardUnload);
    return () => {
      release();
      window.removeEventListener("beforeunload", guardUnload);
    };
  }, [dirty, originPath, originSha256, originIsNew]);
  return <LiveEditContext.Provider value={value}>{children}</LiveEditContext.Provider>;
}

export function useLiveEdit(): LiveEditState {
  return useContext(LiveEditContext);
}

/**
 * Asks before a step that closes the open editor (stop editing, another
 * file or tab, a link away): true when nothing is unsaved or the member
 * agreed to drop the edit.
 */
export function useConfirmLeave(): () => boolean {
  const { live } = useLiveEdit();
  return useCallback(
    () => mayLeaveEdit(live, (question: string) => window.confirm(question)),
    [live]
  );
}

/** A link away from the open editor asks first when the edit is unsaved. */
export function useGuardedLink(): (event: MouseEvent<HTMLAnchorElement>) => void {
  const confirmLeave = useConfirmLeave();
  return (event: MouseEvent<HTMLAnchorElement>) => {
    if (!confirmLeave()) event.preventDefault();
  };
}
