import { afterAll, describe, expect, mock, test } from "bun:test";
import { createContext, useContext, type ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";

import type { ConfirmDialogProps } from "./confirm-dialog";

// A static render draws no Radix portal, so the dialog parts render in place:
// the content shows only while the dialog is open, and the props it is given
// are kept for the test to read. The confirm dialog itself is real.
const Open = createContext(false);
let contentProps: Record<string, unknown> = {};

// A copy, not the namespace: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mock back for the files that run later.
const actualDialog = { ...(await import("./dialog")) };
mock.module("./dialog", () => ({
  ...actualDialog,
  Dialog: ({ open, children }: { open: boolean; children: ReactNode }) => (
    <Open.Provider value={open}>{children}</Open.Provider>
  ),
  DialogTrigger: ({ children }: { children: ReactNode }) => <div data-trigger="">{children}</div>,
  DialogContent: ({ children, ...props }: { children: ReactNode } & Record<string, unknown>) => {
    contentProps = props;
    return useContext(Open) ? <section>{children}</section> : null;
  },
  DialogTitle: ({ children }: { children: ReactNode }) => <h2>{children}</h2>,
  DialogDescription: ({ children }: { children: ReactNode }) => <>{children}</>,
}));
afterAll(() => {
  mock.module("./dialog", () => actualDialog);
});

const { ConfirmDialog } = await import("./confirm-dialog");

function markup(props: Partial<ConfirmDialogProps> = {}): string {
  return renderToStaticMarkup(
    <ConfirmDialog
      title="Remove the key"
      description="The key stops working at once."
      confirmLabel="Remove"
      onConfirm={async () => undefined}
      {...props}
    />
  );
}

describe("ConfirmDialog", () => {
  test("opens from its trigger and starts closed when the caller passes no open", () => {
    const html = markup({ trigger: <button>Remove key</button> });
    expect(html).toContain('<div data-trigger=""><button>Remove key</button></div>');
    expect(html).not.toContain("<section>");
  });

  test("renders open, with no trigger, while the caller holds open true", () => {
    const html = markup({ open: true });
    expect(html).not.toContain("data-trigger");
    expect(html).toContain("<h2>Remove the key</h2>");
    expect(html).toContain("<div>The key stops working at once.</div>");
    expect(html).toContain(">Cancel</button>");
    expect(html).toContain(">Remove</button>");
  });

  test("stays closed while the caller holds open false, even with a trigger", () => {
    const html = markup({ open: false, trigger: <button>Remove key</button> });
    expect(html).toContain("<button>Remove key</button>");
    expect(html).not.toContain("<section>");
  });

  test("hands the content where focus goes once the dialog closes", () => {
    const onCloseAutoFocus = (event: Event) => event.preventDefault();
    markup({ open: true, onCloseAutoFocus });
    expect(contentProps.onCloseAutoFocus).toBe(onCloseAutoFocus);
    markup({ open: true });
    expect(contentProps.onCloseAutoFocus).toBeUndefined();
  });
});
