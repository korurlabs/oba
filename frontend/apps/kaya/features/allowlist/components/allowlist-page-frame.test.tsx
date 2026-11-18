import { afterAll, describe, expect, mock, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

import type { ScopePageProps } from "@/features/scope/components/scope-page";

// The frame is replaced by a stub that prints the options the page asks for.
// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualScope = { ...(await import("@/features/scope/components")) };
mock.module("@/features/scope/components", () => ({
  ...actualScope,
  ScopePage: ({ title, variant = "list" }: ScopePageProps) => (
    <section data-title={title} data-variant={variant} />
  ),
}));
afterAll(() => {
  mock.module("@/features/scope/components", () => actualScope);
});

const { AllowlistPage } = await import("./allowlist-page");

describe("AllowlistPage frame", () => {
  test("keeps the list frame with its pack tree", () => {
    const html = renderToStaticMarkup(<AllowlistPage />);
    expect(html).toContain('data-title="Allowlist"');
    expect(html).toContain('data-variant="list"');
    expect(html).not.toContain("data-bar");
  });
});
