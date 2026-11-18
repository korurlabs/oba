import { describe, expect, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

import type { AccountListResponse } from "../schemas";
import { AccountProviderUsage, PROVIDER_USAGE_FIELDS } from "./account-provider-usage";
import { AccountUsage } from "./account-usage";

const NOW = Date.parse("2026-10-23T12:25:00Z");
const PROVIDERS = ["CLAUDE", "CHATGPT", "GEMINI", "GROK", "DEEPSEEK"] as const;

function account(provider: AccountListResponse["provider"]): AccountListResponse {
  return {
    id: "a",
    provider,
    accountEmail: "ada@example.com",
    users: [],
    machines: 0,
    activeMachines: 0,
    usage: { state: "NO_SNAPSHOT", sharedWith: [] },
  };
}

describe("AccountUsage", () => {
  test("says Not reported for this provider for every provider but Claude", () => {
    for (const provider of PROVIDERS.filter((p: string) => p !== "CLAUDE")) {
      const html = renderToStaticMarkup(<AccountUsage account={account(provider)} now={NOW} />);
      expect(html).toContain("Not reported for this provider");
    }
  });

  test("a Claude account shows its own usage state, not that text", () => {
    const html = renderToStaticMarkup(<AccountUsage account={account("CLAUDE")} now={NOW} />);
    expect(html).not.toContain("Not reported for this provider");
  });
});

describe("AccountProviderUsage", () => {
  test("shows every field as Not connected and no digit, for each provider", () => {
    const fieldCount = PROVIDER_USAGE_FIELDS.reduce(
      (sum: number, group: { fields: readonly string[] }) => sum + group.fields.length,
      0
    );
    for (const provider of PROVIDERS) {
      const html = renderToStaticMarkup(<AccountProviderUsage provider={provider} />);
      const values = html.match(/<dd[^>]*>([^<]*)<\/dd>/g) ?? [];
      expect(values).toHaveLength(fieldCount);
      for (const value of values) expect(value).toContain("Not connected");
      // Strip the markup first: class names and attributes may hold digits.
      const text = html.replace(/<[^>]*>/g, " ");
      expect(text).not.toMatch(/\d/);
      expect(text).toContain("Not connected");
    }
  });
});
