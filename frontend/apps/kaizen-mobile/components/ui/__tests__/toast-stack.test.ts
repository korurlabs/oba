import { describe, expect, it } from "bun:test";

import {
  addToast,
  defaultToastDuration,
  isSameToast,
  MAX_VISIBLE_TOASTS,
  TOAST_DURATION_MS,
  TOAST_WITH_ACTION_DURATION_MS,
  type ToastRecord,
} from "@/components/ui/toast-stack";

function record(id: string, overrides: Partial<ToastRecord> = {}): ToastRecord {
  return {
    id,
    message: `message ${id}`,
    tone: "info",
    durationMs: TOAST_DURATION_MS,
    ...overrides,
  };
}

describe("defaultToastDuration", () => {
  it("gives a toast with an action more time", () => {
    expect(defaultToastDuration(false)).toBe(TOAST_DURATION_MS);
    expect(defaultToastDuration(true)).toBe(TOAST_WITH_ACTION_DURATION_MS);
  });
});

describe("isSameToast", () => {
  it("tells two failures apart by their reason", () => {
    const failed = record("a", { message: "Could not move Web", tone: "error" });
    expect(isSameToast(failed, record("b", { message: "Could not move Web", tone: "error" }))).toBe(
      true
    );
    expect(
      isSameToast(
        { ...failed, description: "Board not found." },
        record("b", { message: "Could not move Web", tone: "error", description: "Forbidden" })
      )
    ).toBe(false);
  });

  it("compares message, tone and action label", () => {
    const base = record("a", { message: "Saved", tone: "success" });
    expect(isSameToast(base, record("b", { message: "Saved", tone: "success" }))).toBe(true);
    expect(isSameToast(base, record("b", { message: "Saved", tone: "error" }))).toBe(false);
    expect(
      isSameToast(
        base,
        record("b", {
          message: "Saved",
          tone: "success",
          action: { label: "Undo", onPress: () => {} },
        })
      )
    ).toBe(false);
  });
});

describe("addToast", () => {
  it("appends a new toast", () => {
    const result = addToast([record("a")], record("b"));
    expect(result.kind).toBe("added");
    expect(result.toasts.map((item) => item.id)).toEqual(["a", "b"]);
  });

  it("merges a duplicate into the visible one and keeps the longer lifetime", () => {
    const visible = record("a", { message: "Offline", durationMs: 4000 });
    const result = addToast([visible], record("b", { message: "Offline", durationMs: 6000 }));
    expect(result).toMatchObject({ kind: "merged", into: "a" });
    expect(result.toasts).toHaveLength(1);
    expect(result.toasts[0].durationMs).toBe(6000);
  });

  it("keeps a sticky toast sticky when a timed duplicate arrives", () => {
    const result = addToast(
      [record("a", { durationMs: 0 })],
      record("b", { message: "message a" })
    );
    expect(result.toasts[0].durationMs).toBe(0);
  });

  it("takes the newest action handler on a merge", () => {
    const first = () => {};
    const second = () => {};
    const visible = record("a", { action: { label: "Undo", onPress: first } });
    const result = addToast(
      [visible],
      record("b", { message: "message a", action: { label: "Undo", onPress: second } })
    );
    expect(result.toasts[0].action?.onPress).toBe(second);
  });

  it("drops the oldest toast past the cap", () => {
    const visible = Array.from({ length: MAX_VISIBLE_TOASTS }, (_, index) => record(`t${index}`));
    const result = addToast(visible, record("new"));
    expect(result).toMatchObject({ kind: "added", dropped: ["t0"] });
    expect(result.toasts).toHaveLength(MAX_VISIBLE_TOASTS);
    expect(result.toasts.at(-1)?.id).toBe("new");
  });

  it("never mutates the previous stack", () => {
    const visible = [record("a")];
    addToast(visible, record("b"));
    expect(visible.map((item) => item.id)).toEqual(["a"]);
  });
});
