import { afterAll, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";

import type { Owner } from "../schemas";

// The dialog's content sits in a portal that a static render leaves out, so the
// dialog shows it in place; the mutation is replaced. The dialog body is real.
const actualDialog = { ...(await import("@oba/ui/sky/dialog")) };
const actualOwners = { ...(await import("../hooks/use-owners")) };
const passthrough = ({ children }: { children?: ReactNode }) => <div>{children}</div>;
mock.module("@oba/ui/sky/dialog", () => ({
  ...actualDialog,
  Dialog: passthrough,
  DialogTrigger: ({ children }: { children: ReactNode }) => <>{children}</>,
  DialogContent: passthrough,
  DialogHeader: passthrough,
  DialogFooter: passthrough,
  DialogTitle: ({ children }: { children: ReactNode }) => <h2>{children}</h2>,
  DialogDescription: ({ children }: { children: ReactNode }) => <p>{children}</p>,
}));
mock.module("../hooks/use-owners", () => ({
  ...actualOwners,
  useCreatePack: () => ({ isPending: false, mutate: () => undefined }),
}));
afterAll(() => {
  mock.module("@oba/ui/sky/dialog", () => actualDialog);
  mock.module("../hooks/use-owners", () => actualOwners);
});

const { NewPackDialog } = await import("./new-pack-dialog");

const pack = (id: string, name: string): Owner => ({
  id,
  name,
  parentPackId: null,
  currentVersionId: null,
  required: false,
  mountPaths: [],
  repositoryIds: [],
});
const OWNERS: Owner[] = [
  pack("p-village", "village"),
  pack("p-org", "org"),
  pack("p-kagi", "kagi"),
];

function render(defaultParentId: string | null): string {
  return renderToStaticMarkup(
    <NewPackDialog
      owners={OWNERS}
      defaultParentId={defaultParentId}
      trigger={<button type="button">New pack</button>}
      onCreated={() => undefined}
    />
  );
}

/** The `<option>` tags of the Parent select, in order. */
function options(html: string): string[] {
  return html.match(/<option[^>]*>[^<]*<\/option>/g) ?? [];
}

describe("NewPackDialog parent choice", () => {
  test("the title is always New pack and a Parent select lists None and every pack by name", () => {
    const html = render("p-village");
    expect(html).toContain("<h2>New pack</h2>");
    expect(html).toContain("Parent");
    const labels = options(html).map(
      (option: string) => /^<option[^>]*>([^<]*)<\/option>$/.exec(option)?.[1] ?? ""
    );
    expect(labels).toEqual(["None", "kagi", "org", "village"]);
  });

  test("the select starts at the selected pack", () => {
    const html = render("p-village");
    const selected = options(html).filter((option: string) => option.includes("selected"));
    expect(selected).toHaveLength(1);
    expect(selected[0]).toContain('value="p-village"');
    expect(html).toContain("The child pack inherits every item of village");
  });

  test("without a default parent the select starts at None and the dialog describes a root pack", () => {
    const html = render(null);
    const selected = options(html).filter((option: string) => option.includes("selected"));
    expect(selected).toHaveLength(1);
    expect(selected[0]).toContain('value=""');
    expect(html).toContain("A root pack has no parent.");
  });

  test("a default parent that is not a pack of the organization falls back to None", () => {
    const html = render("p-gone");
    expect(html).toContain("A root pack has no parent.");
  });
});
