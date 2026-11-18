"use client";

/**
 * Ticket draft carry-over
 *
 * The ticket create/edit form exists in two shells that live at the SAME
 * URL: an intercepted modal (fast path) and the full page (hard
 * navigation). Pressing the dialog's expand button performs a hard
 * navigation so the page shell renders instead of the modal — which
 * remounts React and would otherwise drop whatever the user had typed.
 *
 * To bridge that gap the dialog serialises its current `TicketFormValues`
 * into `sessionStorage` immediately before navigating; the page shell
 * consumes the draft exactly once on mount and then removes the key.
 *
 * Guards against stale drafts:
 * - the key embeds the board + ticket discriminator, and the stored
 *   payload repeats it, so a draft is never applied to another ticket;
 * - a timestamp expires anything older than `DRAFT_MAX_AGE_MS` (covers a
 *   navigation that never completed, e.g. the user hit stop);
 * - the entry is removed on read, so a refresh of the page shell shows
 *   server data rather than replaying the draft forever.
 *
 * Storage access never throws into the render path: Safari private mode
 * and quota failures are logged and degrade to "no draft" (see
 * `.agents/rules/10-no-silent-failures.md`).
 */

import { parseISO } from "date-fns";
import type { TicketFormValues } from "../schemas";

const STORAGE_PREFIX = "kazan:ticket-draft:";
const DRAFT_MAX_AGE_MS = 5 * 60 * 1000;

export interface TicketDraftTarget {
  /**
   * Which form wrote the draft. REQUIRED, and part of the key.
   *
   * `useTicketForm` has an internal create mode that the url does not
   * reflect: hitting "+ Add" on the sub-ticket list flips `mode` to
   * "create" while the `ticketId` prop still points at the PARENT. Without
   * this discriminator a sub-ticket draft and the parent's edit form share
   * a key, and expanding would replay the half-typed sub-ticket into the
   * parent's edit form — where Save would persist it over the real ticket.
   *
   * `mode` alone does not separate the two CREATE forms from each other
   * (both carry `ticketId: null`); `parentId` below does that.
   */
  mode: "create" | "edit";
  /** Board the form is scoped to. Empty string is allowed (board-less create). */
  boardId: string;
  /** Ticket being edited, or `null` for the create form. */
  ticketId: string | null;
  /**
   * Parent ticket for a create form, or `null`. Without it the plain create
   * form and the sub-ticket create form would share `create:<board>:new`
   * and could replay each other's draft.
   */
  parentId?: string | null;
}

interface StoredTicketDraft {
  target: string;
  savedAt: number;
  values: Record<string, unknown>;
}

function targetId({ mode, boardId, ticketId, parentId }: TicketDraftTarget): string {
  return `${mode}:${boardId || "none"}:${ticketId ?? "new"}:${parentId ?? "noparent"}`;
}

function storageKey(target: TicketDraftTarget): string {
  return `${STORAGE_PREFIX}${targetId(target)}`;
}

/**
 * Persists the current form values for `target`. Call this immediately
 * before a hard navigation to the full-page shell.
 *
 * Returns `false` when the draft could NOT be stored. Callers must surface
 * that and abort the navigation — otherwise the hard nav throws away
 * everything the user typed with no way to get it back
 * (`.agents/rules/10-no-silent-failures.md`).
 */
export function saveTicketDraft(
  target: TicketDraftTarget,
  values: TicketFormValues,
): boolean {
  if (typeof window === "undefined") return false;

  const payload: StoredTicketDraft = {
    target: targetId(target),
    savedAt: Date.now(),
    values: {
      ...values,
      // Date is not JSON-serialisable in a round-trippable way — store ISO.
      dueDate: values.dueDate ? values.dueDate.toISOString() : null,
    },
  };

  try {
    window.sessionStorage.setItem(storageKey(target), JSON.stringify(payload));
    return true;
  } catch (error) {
    // Quota exceeded, or sessionStorage blocked (Safari private mode).
    // Logged here AND reported to the caller, which surfaces it to the user
    // and cancels the navigation rather than silently losing their work.
    console.warn("[ticket-draft] could not persist draft:", error);
    return false;
  }
}

/**
 * Reads and REMOVES the draft stored for `target`. Returns `null` when
 * there is no draft, when it belongs to another ticket, when it has
 * expired, or when it cannot be parsed.
 */
export function consumeTicketDraft(target: TicketDraftTarget): TicketFormValues | null {
  if (typeof window === "undefined") return null;

  const key = storageKey(target);
  let raw: string | null = null;
  try {
    raw = window.sessionStorage.getItem(key);
    if (raw !== null) window.sessionStorage.removeItem(key);
  } catch (error) {
    console.warn(
      "[ticket-draft] sessionStorage unavailable — falling back to server data:",
      error,
    );
    return null;
  }
  if (!raw) return null;

  try {
    const parsed = JSON.parse(raw) as StoredTicketDraft | null;
    if (!parsed || typeof parsed !== "object" || typeof parsed.values !== "object") {
      return null;
    }
    // Rejects a draft written by a different form: another ticket, another
    // board, or — critically — the internal sub-ticket CREATE form whose
    // ticketId is the parent's.
    if (parsed.target !== targetId(target)) return null;
    if (typeof parsed.savedAt !== "number" || !Number.isFinite(parsed.savedAt)) return null;
    if (Date.now() - parsed.savedAt > DRAFT_MAX_AGE_MS) return null;

    const values = parsed.values as Omit<TicketFormValues, "dueDate"> & {
      dueDate?: string | null;
    };
    return {
      ...values,
      dueDate: typeof values.dueDate === "string" ? parseISO(values.dueDate) : null,
    } as TicketFormValues;
  } catch (error) {
    console.warn(
      "[ticket-draft] draft could not be parsed — falling back to server data:",
      error,
    );
    return null;
  }
}
