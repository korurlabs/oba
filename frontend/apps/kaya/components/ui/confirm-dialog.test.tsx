import { afterAll, afterEach, beforeEach, describe, expect, mock, test } from "bun:test";
import {
  Children,
  cloneElement,
  createContext,
  isValidElement,
  useContext,
  useState,
  type ReactElement,
  type ReactNode,
} from "react";

import { KayaApiError } from "@/lib/api/errors";
import { cleanup, renderLive } from "@oba/shared/testing/live-render";

// The dialog shell is fixed: a Radix portal needs a browser, so its parts render
// in place and the content shows only while the dialog is open. The confirm
// flow, its busy state and the refusal in Kaya's words are real.
const DialogState = createContext<{ open: boolean; set: (open: boolean) => void }>({
  open: false,
  set: () => undefined,
});

// A copy, not the namespace: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mock back for the files that run later.
const actualDialog = { ...(await import("@oba/ui/sky/dialog")) };
mock.module("@oba/ui/sky/dialog", () => ({
  ...actualDialog,
  Dialog: ({
    open,
    onOpenChange,
    children,
  }: {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    children: ReactNode;
  }) => <DialogState.Provider value={{ open, set: onOpenChange }}>{children}</DialogState.Provider>,
  DialogTrigger: ({ children }: { children: ReactNode }) => {
    const { set } = useContext(DialogState);
    const only = Children.only(children);
    return isValidElement(only)
      ? cloneElement(only as ReactElement<{ onClick: () => void }>, { onClick: () => set(true) })
      : only;
  },
  DialogContent: ({ children }: { children: ReactNode }) =>
    useContext(DialogState).open ? <div>{children}</div> : null,
  DialogHeader: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DialogFooter: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DialogTitle: ({ children }: { children: ReactNode }) => <h2>{children}</h2>,
  DialogDescription: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));
afterAll(() => {
  mock.module("@oba/ui/sky/dialog", () => actualDialog);
});
afterEach(cleanup);

const { ConfirmDialog } = await import("./confirm-dialog");

const TITLE = "Retire ada-laptop";
const RETIRED = "The machine is retired. Register it again to get a new token.";

/** An action that runs until the test settles it. */
function pendingAction() {
  let settle: { resolve: () => void; reject: (error: unknown) => void } = {
    resolve: () => undefined,
    reject: () => undefined,
  };
  const run = () =>
    new Promise<void>((resolve, reject) => {
      settle = { resolve, reject };
    });
  return { run, resolve: () => settle.resolve(), reject: (error: unknown) => settle.reject(error) };
}

const refusal = () => new KayaApiError("Machine is retired", 409, "KAY_VAL_011");

/** Every open and close the controlled dialog reported, in order. */
let changes: boolean[] = [];
beforeEach(() => {
  changes = [];
});

/** A caller that opens the dialog from a control outside it, as a row menu does. */
function MenuCaller({ onConfirm }: { onConfirm: () => Promise<unknown> }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button onClick={() => setOpen(true)}>Open from the menu</button>
      <ConfirmDialog
        open={open}
        onOpenChange={(next: boolean) => {
          changes.push(next);
          setOpen(next);
        }}
        title={TITLE}
        description="Its sessions stay."
        confirmLabel="Retire"
        destructive
        onConfirm={onConfirm}
      />
    </>
  );
}

describe("ConfirmDialog opened from its trigger", () => {
  function dialog(onConfirm: () => Promise<unknown>) {
    return (
      <ConfirmDialog
        trigger={<button>Retire machine</button>}
        title={TITLE}
        description="Its sessions stay."
        confirmLabel="Retire"
        destructive
        onConfirm={onConfirm}
      />
    );
  }

  test("is busy while the action runs and closes once it succeeds", async () => {
    const action = pendingAction();
    const screen = await renderLive(dialog(action.run));
    expect(screen.byText(TITLE)).toBeNull();

    await screen.byText("Retire machine")?.click();
    await screen.byText("Retire")?.click();
    expect(screen.byText("Retire")?.disabled).toBe(true);
    expect(screen.byText("Cancel")?.disabled).toBe(true);

    await screen.act(() => action.resolve());
    expect(screen.byText(TITLE)).toBeNull();
  });

  test("stays open with the refusal in Kaya's words, and drops it once cancelled", async () => {
    const action = pendingAction();
    const screen = await renderLive(dialog(action.run));
    await screen.byText("Retire machine")?.click();
    await screen.byText("Retire")?.click();
    await screen.act(() => action.reject(refusal()));

    expect(screen.byText(TITLE)).not.toBeNull();
    expect(screen.byAttribute("role", "alert").map((alert) => alert.text)).toEqual([RETIRED]);
    expect(screen.byText("Retire")?.disabled).toBe(false);

    await screen.byText("Cancel")?.click();
    expect(screen.byText(TITLE)).toBeNull();
    await screen.byText("Retire machine")?.click();
    expect(screen.byAttribute("role", "alert")).toEqual([]);
  });
});

describe("ConfirmDialog opened through open", () => {
  test("renders no trigger, is busy while the action runs and asks the caller to close", async () => {
    const action = pendingAction();
    const screen = await renderLive(<MenuCaller onConfirm={action.run} />);
    expect(screen.byText(TITLE)).toBeNull();

    await screen.byText("Open from the menu")?.click();
    expect(screen.byText(TITLE)).not.toBeNull();
    expect(screen.byTag("button").map((button) => button.text)).toEqual([
      "Open from the menu",
      "Cancel",
      "Retire",
    ]);

    await screen.byText("Retire")?.click();
    expect(screen.byText("Retire")?.disabled).toBe(true);
    expect(screen.byText("Cancel")?.disabled).toBe(true);

    await screen.act(() => action.resolve());
    expect(changes).toEqual([false]);
    expect(screen.byText(TITLE)).toBeNull();
  });

  test("stays open with the refusal shown, and Cancel asks the caller to close", async () => {
    const action = pendingAction();
    const screen = await renderLive(<MenuCaller onConfirm={action.run} />);
    await screen.byText("Open from the menu")?.click();
    await screen.byText("Retire")?.click();
    await screen.act(() => action.reject(refusal()));

    expect(changes).toEqual([]);
    expect(screen.byText(TITLE)).not.toBeNull();
    expect(screen.byAttribute("role", "alert").map((alert) => alert.text)).toEqual([RETIRED]);
    expect(screen.byText("Retire")?.disabled).toBe(false);

    await screen.byText("Cancel")?.click();
    expect(changes).toEqual([false]);
    expect(screen.byText(TITLE)).toBeNull();
    await screen.byText("Open from the menu")?.click();
    expect(screen.byAttribute("role", "alert")).toEqual([]);
  });

  test("stays closed while the caller holds it closed, even when its trigger is pressed", async () => {
    const screen = await renderLive(
      <ConfirmDialog
        open={false}
        onOpenChange={(next: boolean) => changes.push(next)}
        trigger={<button>Retire machine</button>}
        title={TITLE}
        description="Its sessions stay."
        confirmLabel="Retire"
        onConfirm={async () => undefined}
      />
    );
    await screen.byText("Retire machine")?.click();
    expect(changes).toEqual([true]);
    expect(screen.byText(TITLE)).toBeNull();
  });
});
