"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useSyncExternalStore,
  type ReactNode,
} from "react";

import { InfoTip } from "@oba/ui/sky/info-tip";
import { KayaApiError } from "@/lib/api/errors";
import { useIdentity } from "@/lib/identity/identity-context";

import { useVersion } from "../hooks/use-versions";
import type { VersionDetailResponse } from "../schemas";

export type OpenChange = {
  /** The stored change id, before it is known to be the viewer's open change. */
  changeId: string | null;
  /** The stored change, once it is known to be the viewer's DRAFT. */
  change: VersionDetailResponse | null;
  /** A stored change id is still being read. */
  loading: boolean;
  /** Opens a change (or forgets the open one with null) on every page and tab of this browser. */
  setChangeId: (id: string | null) => void;
};

const OpenChangeContext = createContext<OpenChange | null>(null);

/** One open change per organization and member, so neither picks up another's. */
export function openChangeKey(organizationId: string, userId: string): string {
  return `kaya.openChange.${organizationId}.${userId}`;
}

/**
 * Where an id lives when the browser refuses storage (a private window,
 * blocked site data), so the open change still works in this tab.
 */
const memoryStore = new Map<string, string>();

const storeListeners = new Set<() => void>();

function readStoredId(key: string): string | null {
  try {
    return window.localStorage.getItem(key);
  } catch {
    // Storage is refused (a private window, blocked site data): this tab's memory
    // stands in, where no id means no open change.
    return memoryStore.get(key) ?? null;
  }
}

function writeStoredId(key: string, id: string | null): void {
  try {
    if (id === null) window.localStorage.removeItem(key);
    else window.localStorage.setItem(key, id);
  } catch {
    // Storage is refused: the id lives in this tab's memory, so other tabs and a
    // reload see no open change.
    if (id === null) memoryStore.delete(key);
    else memoryStore.set(key, id);
  }
  storeListeners.forEach((listener: () => void) => listener());
}

/** Writes in this tab notify at once; the `storage` event brings writes of other tabs. */
function subscribeStore(listener: () => void): () => void {
  storeListeners.add(listener);
  window.addEventListener("storage", listener);
  return () => {
    storeListeners.delete(listener);
    window.removeEventListener("storage", listener);
  };
}

/** The open change id of `key`; null on the server, which has no storage. */
function useStoredId(key: string): string | null {
  const read = useCallback(() => readStoredId(key), [key]);
  return useSyncExternalStore(subscribeStore, read, () => null);
}

/**
 * The member's open change: a change of one pack that every edit of that
 * pack saves into until it is submitted or closed. It lives in the browser,
 * so it follows the member across pages and reloads, and is forgotten once
 * it is no longer the member's DRAFT (submitted elsewhere, deleted, another
 * member's).
 */
export function OpenChangeProvider({ children }: { children: ReactNode }) {
  const { me, organization } = useIdentity();
  const key = openChangeKey(organization.organizationId, me.id);
  const changeId = useStoredId(key);
  // Read again on focus, so a change submitted or deleted in another tab is forgotten here too.
  const version = useVersion(changeId ?? "", { live: true });
  const detail = changeId !== null ? (version.data?.data ?? null) : null;
  const owned = detail !== null && detail.status === "DRAFT" && detail.proposedBy === me.id;
  const missing =
    changeId !== null && version.error instanceof KayaApiError && version.error.status === 404;
  const forget = (detail !== null && !owned) || missing;

  const setChangeId = useCallback((id: string | null) => writeStoredId(key, id), [key]);

  useEffect(() => {
    if (forget) setChangeId(null);
  }, [forget, setChangeId]);

  const change = owned ? detail : null;
  const loading = changeId !== null && detail === null && version.error === null;
  const value = useMemo<OpenChange>(
    () => ({ changeId, change, loading, setChangeId }),
    [changeId, change, loading, setChangeId]
  );
  return <OpenChangeContext.Provider value={value}>{children}</OpenChangeContext.Provider>;
}

/** The open change; only inside the dashboard, below `OpenChangeProvider`. */
export function useOpenChange(): OpenChange {
  const open = useContext(OpenChangeContext);
  if (open === null) {
    throw new Error("useOpenChange is used outside OpenChangeProvider");
  }
  return open;
}

/**
 * Change mode: while a change is open, a frame in the change colour runs
 * around the window. It takes no pointer events and no focus, and sits below
 * dialogs and popovers, so it never stands between the member and the page.
 */
export function ChangeModeFrame() {
  const { change } = useOpenChange();
  if (change === null) return null;
  return (
    <div
      aria-hidden
      data-change-mode
      className="border-korur-blue bg-korur-blue/5 pointer-events-none fixed inset-0 z-40 border-4"
    />
  );
}

/** Why an edit of another pack is disabled while a change is open. */
export function changeLockText(packName: string): string {
  return `Submit or close your change of ${packName} first.`;
}

/**
 * Sits beside an edit action of another pack while a change is open. The
 * tip is its own button, so it stays reachable while the action is disabled.
 */
export function ChangeLockTip({ packName }: { packName: string }) {
  return <InfoTip label="Why is this disabled?">{changeLockText(packName)}</InfoTip>;
}
