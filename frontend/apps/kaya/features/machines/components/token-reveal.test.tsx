import { afterAll, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";

// The dialog's content sits in a portal that a static render leaves out, so the
// dialog shows it in place. The dialog body is real.
const actualDialog = { ...(await import("@oba/ui/sky/dialog")) };
const passthrough = ({ children }: { children?: ReactNode }) => <div>{children}</div>;
mock.module("@oba/ui/sky/dialog", () => ({
  ...actualDialog,
  Dialog: passthrough,
  DialogContent: passthrough,
  DialogHeader: passthrough,
  DialogFooter: passthrough,
  DialogTitle: ({ children }: { children: ReactNode }) => <h2>{children}</h2>,
  DialogDescription: ({ children }: { children: ReactNode }) => <p>{children}</p>,
}));
afterAll(() => {
  mock.module("@oba/ui/sky/dialog", () => actualDialog);
});

const { TokenReveal } = await import("./token-reveal");

const TOKEN = "kyt_secret";

function render(): string {
  return renderToStaticMarkup(
    <TokenReveal token={TOKEN} machineName="laptop" onClose={() => undefined} />
  );
}

describe("TokenReveal", () => {
  test("shows the stdin command and leaves the token out of it", () => {
    const html = render();
    expect(html).toContain(
      "kagi secrets set --personal --path /kaya/recorder --from-file /dev/stdin"
    );
    expect(html).toContain("KAYA_RECORDER_TOKEN=</code>, paste the token");
    expect(html).toContain("press Enter and then Ctrl-D");
    expect(html.split(TOKEN)).toHaveLength(2);
    expect(html).not.toContain(`KAYA_RECORDER_TOKEN=${TOKEN}`);
  });
});
