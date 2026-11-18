import { describe, expect, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

import {
  CollapsibleSearch,
  isSearchOpen,
  isSearchShortcut,
  nextExpanded,
} from "./collapsible-search";

const KEY = { key: "/", ctrlKey: false, metaKey: false, altKey: false, target: null };

function markup(value: string): string {
  return renderToStaticMarkup(
    <CollapsibleSearch value={value} onChange={() => undefined} label="Search skills" />
  );
}

describe("CollapsibleSearch", () => {
  test("renders only the labelled icon button while empty and collapsed", () => {
    const html = markup("");
    expect(html).toContain('aria-label="Search skills"');
    expect(html).toContain("<button");
    expect(html).not.toContain("<input");
  });

  test("renders the input with its text while it holds a search", () => {
    const html = markup("kazan");
    expect(html).toContain("<input");
    expect(html).toContain('value="kazan"');
    expect(html).not.toContain("<button");
  });

  test("a click expands it", () => {
    const expanded = nextExpanded(false, { type: "expand" });
    expect(expanded).toBe(true);
    expect(isSearchOpen(expanded, "")).toBe(true);
  });

  test("Escape collapses it", () => {
    expect(nextExpanded(true, { type: "escape" })).toBe(false);
    expect(isSearchOpen(false, "")).toBe(false);
  });

  test("leaving it collapses it only when it is empty", () => {
    expect(nextExpanded(true, { type: "blur", value: "" })).toBe(false);
    expect(nextExpanded(true, { type: "blur", value: "kai" })).toBe(true);
    expect(isSearchOpen(false, "kai")).toBe(true);
  });

  test("a bare slash outside a text field is the shortcut", () => {
    expect(isSearchShortcut(KEY)).toBe(true);
    expect(isSearchShortcut({ ...KEY, target: { tagName: "BUTTON" } })).toBe(true);
  });

  test("a slash in a text field or with a modifier is not the shortcut", () => {
    expect(isSearchShortcut({ ...KEY, target: { tagName: "INPUT" } })).toBe(false);
    expect(isSearchShortcut({ ...KEY, target: { tagName: "TEXTAREA" } })).toBe(false);
    expect(isSearchShortcut({ ...KEY, target: { tagName: "DIV", isContentEditable: true } })).toBe(
      false
    );
    expect(isSearchShortcut({ ...KEY, ctrlKey: true })).toBe(false);
    expect(isSearchShortcut({ ...KEY, key: "a" })).toBe(false);
  });
});
