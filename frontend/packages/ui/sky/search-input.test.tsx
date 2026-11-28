import { describe, expect, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

import { ListToolbar } from "./list-toolbar";
import { SearchInput, adoptIncoming, normalizeSearch } from "./search-input";

function markup(value: string | null, className?: string): string {
  return renderToStaticMarkup(
    <SearchInput
      value={value}
      onSearchChange={() => undefined}
      label="Search campaigns"
      clearLabel="Clear search"
      className={className}
    />
  );
}

describe("normalizeSearch", () => {
  test("trims the text and turns nothing into undefined", () => {
    expect(normalizeSearch("  acme ")).toBe("acme");
    expect(normalizeSearch("   ")).toBeUndefined();
    expect(normalizeSearch("")).toBeUndefined();
    expect(normalizeSearch(null)).toBeUndefined();
    expect(normalizeSearch(undefined)).toBeUndefined();
  });
});

describe("adoptIncoming", () => {
  test("keeps the typed text when the app echoes its trimmed form", () => {
    expect(adoptIncoming("acme ", "acme")).toBe("acme ");
  });

  test("takes a value the app set itself", () => {
    expect(adoptIncoming("acme", "")).toBe("");
    expect(adoptIncoming("acme", null)).toBe("");
    expect(adoptIncoming("", "kazan")).toBe("kazan");
  });
});

describe("SearchInput", () => {
  test("names the field with its label, which is also the default placeholder", () => {
    const html = markup(null);
    expect(html).toContain('aria-label="Search campaigns"');
    expect(html).toContain('placeholder="Search campaigns"');
    expect(html).toContain("lucide-search");
  });

  test("shows a named clear button only while it holds text", () => {
    expect(markup(null)).not.toContain("<button");
    const html = markup("acme");
    expect(html).toContain('value="acme"');
    expect(html).toMatch(/<button type="button" aria-label="Clear search"/);
  });

  test("has the toolbar's width, which a class overrides", () => {
    expect(markup(null)).toContain("w-40 sm:w-56");
    const html = markup(null, "w-full");
    expect(html).toContain("w-full");
    expect(html).not.toContain("w-40");
  });

  test("sits first in the list toolbar", () => {
    const html = renderToStaticMarkup(
      <ListToolbar
        search={<SearchInput onSearchChange={() => undefined} label="Search" clearLabel="Clear" />}
        create={<span>CREATE</span>}
      />
    );
    expect(html.indexOf('data-slot="search-input"')).toBeGreaterThan(-1);
    expect(html.indexOf('data-slot="search-input"')).toBeLessThan(html.indexOf("CREATE"));
  });
});
