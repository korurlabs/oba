/*
 * The pure half of the toast layer: what the visible stack becomes when a toast
 * arrives. It has no react-native import, so `bun test` can assert it.
 *
 * One failing request can raise the same message several times (query retries,
 * refetches on focus or reconnect). Two rules keep that from stacking copies:
 *   - a toast identical to a visible one is merged into it, and the provider
 *     restarts that toast's timer;
 *   - at most MAX_VISIBLE_TOASTS are visible, and the oldest is dropped first.
 */

export type ToastTone = "success" | "error" | "info" | "warning";

/** Most toasts on screen at once. */
export const MAX_VISIBLE_TOASTS = 3;

/** How long a toast stays: longer when it offers an action the user may want. */
export const TOAST_DURATION_MS = 4000;
export const TOAST_WITH_ACTION_DURATION_MS = 6000;

export type ToastAction = {
  label: string;
  onPress: () => void;
};

export type ToastRecord = {
  id: string;
  message: string;
  /** A second line under the message, such as the server's reason for a failure. */
  description?: string;
  tone: ToastTone;
  action?: ToastAction;
  /** Auto-dismiss delay; 0 or less keeps the toast until it is tapped. */
  durationMs: number;
};

export type ToastAddResult =
  /** An identical toast was visible: `into` is its id, and its timer restarts. */
  | { kind: "merged"; into: string; toasts: ToastRecord[] }
  /** The toast was appended; `dropped` are the ids the cap pushed out. */
  | { kind: "added"; toasts: ToastRecord[]; dropped: string[] };

/** The default lifetime of a toast with or without an action. */
export function defaultToastDuration(hasAction: boolean): number {
  return hasAction ? TOAST_WITH_ACTION_DURATION_MS : TOAST_DURATION_MS;
}

/** Same text, tone and action label: the user would read the two as one message. */
export function isSameToast(a: ToastRecord, b: ToastRecord): boolean {
  return (
    a.tone === b.tone &&
    a.message === b.message &&
    (a.description ?? "") === (b.description ?? "") &&
    (a.action?.label ?? "") === (b.action?.label ?? "")
  );
}

/** The longer lifetime; a toast kept until tapped wins over any timed one. */
function longerDuration(a: number, b: number): number {
  if (a <= 0 || b <= 0) return 0;
  return Math.max(a, b);
}

/** The visible stack after `next` arrives. Never mutates `prev`. */
export function addToast(
  prev: readonly ToastRecord[],
  next: ToastRecord,
  maxVisible: number = MAX_VISIBLE_TOASTS
): ToastAddResult {
  const existing = prev.find((item) => isSameToast(item, next));
  if (existing) {
    const durationMs = longerDuration(existing.durationMs, next.durationMs);
    return {
      kind: "merged",
      into: existing.id,
      // The newest action handler wins: it closes over the newest screen state.
      toasts: prev.map((item) =>
        item.id === existing.id ? { ...item, durationMs, action: next.action ?? item.action } : item
      ),
    };
  }

  const appended = [...prev, next];
  const overflow = Math.max(0, appended.length - Math.max(1, maxVisible));
  return {
    kind: "added",
    toasts: appended.slice(overflow),
    dropped: appended.slice(0, overflow).map((item) => item.id),
  };
}
