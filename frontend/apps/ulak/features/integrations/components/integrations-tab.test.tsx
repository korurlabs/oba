import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import en from "@/locales/en.json";

// The messages and the card are real; the link is a plain anchor and the
// connection list is a fixed value.
type ListResult = { data?: { data: unknown[] }; isLoading: boolean; isError: boolean };

let list: ListResult;

const actualNavigation = { ...(await import("@oba/shared/libs/i18n/navigation")) };
const actualHooks = { ...(await import("../hooks/use-azure-connections")) };

mock.module("@oba/shared/libs/i18n/navigation", () => ({
  ...actualNavigation,
  Link: ({ href, children }: { href: string; children?: ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));
mock.module("../hooks/use-azure-connections", () => ({
  ...actualHooks,
  useAzureConnections: () => list,
}));

afterAll(() => {
  mock.module("@oba/shared/libs/i18n/navigation", () => actualNavigation);
  mock.module("../hooks/use-azure-connections", () => actualHooks);
});

const { IntegrationsTab } = await import("./integrations-tab");

function render(): string {
  return renderToStaticMarkup(
    <NextIntlClientProvider locale="en" messages={en} timeZone="UTC">
      <IntegrationsTab organizationId="org-1" />
    </NextIntlClientProvider>
  );
}

beforeEach(() => {
  list = { data: { data: [{}, {}] }, isLoading: false, isError: false };
});

describe("IntegrationsTab", () => {
  test("shows the Azure card with its connection count and a link to its page", () => {
    const html = render();

    expect(html).toContain(en.integrations.providers.azure.name);
    expect(html).toContain("2 connections");
    expect(html).toContain('href="/integrations/azure"');
  });

  test("counts no connection and one connection in words", () => {
    list = { data: { data: [] }, isLoading: false, isError: false };
    expect(render()).toContain("No connections");
    list = { data: { data: [{}] }, isLoading: false, isError: false };
    expect(render()).toContain("1 connection");
  });

  test("shows no count while the list loads", () => {
    list = { isLoading: true, isError: false };

    const html = render();

    expect(html).not.toContain("connection<");
    expect(html).toContain('href="/integrations/azure"');
  });

  test("says when the connections could not be loaded, and keeps the link", () => {
    list = { isLoading: false, isError: true };

    const html = render();

    expect(html).toContain(en.integrations.azure.loadError);
    expect(html).toContain('href="/integrations/azure"');
  });
});
