import { describe, expect, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

import {
  FilterPopover,
  FilterPopoverPanel,
  categoryActiveCount,
  hasOptionSearch,
  nextSingleValue,
  shownCategory,
  toggleMultiValue,
  totalActiveCount,
  visibleOptions,
  type FilterCategory,
  type FilterPopoverLabels,
  type MultiFilterCategory,
  type SingleFilterCategory,
} from "./filter-popover";

const LABELS: FilterPopoverLabels = {
  trigger: "Filter",
  chooseCategory: "Select a field",
  searchOptions: "Search options",
  noOptions: "No options",
  loading: "Loading",
  clear: "Clear all",
};

function status(value: string[] = []): MultiFilterCategory {
  return {
    key: "status",
    label: "Status",
    mode: "multi",
    value,
    onChange: () => undefined,
    options: [
      { value: "OPEN", label: "Open" },
      { value: "DONE", label: "Done" },
    ],
  };
}

function language(value: string | null = null): SingleFilterCategory {
  return {
    key: "language",
    label: "Language",
    mode: "single",
    value,
    onChange: () => undefined,
    searchable: true,
    options: [
      { value: "en", label: "English", hint: "EN" },
      { value: "nl", label: "Dutch", hint: "NL" },
      { value: "tr", label: "Turkish", hint: "TR" },
    ],
  };
}

function relation(overrides: Partial<SingleFilterCategory> = {}): SingleFilterCategory {
  return {
    key: "relation",
    label: "Relation",
    mode: "single",
    value: null,
    onChange: () => undefined,
    onSearchChange: () => undefined,
    options: [{ value: "r1", label: "Acme" }],
    ...overrides,
  };
}

function panel(categories: FilterCategory[], selectedKey: string | null, search = ""): string {
  return renderToStaticMarkup(
    <FilterPopoverPanel
      categories={categories}
      selectedKey={selectedKey}
      onSelectCategory={() => undefined}
      search={search}
      onSearchChange={() => undefined}
      onClear={() => undefined}
      labels={LABELS}
    />
  );
}

describe("active counts", () => {
  test("a multi select counts each value, a single select at most one", () => {
    expect(categoryActiveCount(status())).toBe(0);
    expect(categoryActiveCount(status(["OPEN", "DONE"]))).toBe(2);
    expect(categoryActiveCount(language())).toBe(0);
    expect(categoryActiveCount(language("nl"))).toBe(1);
    expect(totalActiveCount([status(["OPEN", "DONE"]), language("nl")])).toBe(3);
  });
});

describe("choosing values", () => {
  test("a multi select ticks and unticks an option", () => {
    expect(toggleMultiValue([], "OPEN")).toEqual(["OPEN"]);
    expect(toggleMultiValue(["OPEN", "DONE"], "OPEN")).toEqual(["DONE"]);
  });

  test("a single select replaces its value and clears it when chosen again", () => {
    expect(nextSingleValue(null, "en")).toBe("en");
    expect(nextSingleValue("en", "nl")).toBe("nl");
    expect(nextSingleValue("nl", "nl")).toBeNull();
  });
});

describe("option search", () => {
  test("a static searchable list narrows on label and hint, ignoring case", () => {
    expect(visibleOptions(language(), "dut").map((option) => option.value)).toEqual(["nl"]);
    expect(visibleOptions(language(), " TR ").map((option) => option.value)).toEqual(["tr"]);
    expect(visibleOptions(language(), "")).toHaveLength(3);
  });

  test("a server search shows its options as given", () => {
    expect(visibleOptions(relation(), "zzz")).toHaveLength(1);
  });

  test("a list without search ignores the text", () => {
    expect(visibleOptions(status(), "open")).toHaveLength(2);
  });

  test("only searchable and server-searched categories show a search box", () => {
    expect(hasOptionSearch(status())).toBe(false);
    expect(hasOptionSearch(language())).toBe(true);
    expect(hasOptionSearch(relation())).toBe(true);
  });
});

describe("shownCategory", () => {
  test("shows the chosen category, the only one, or none", () => {
    expect(shownCategory([status(), language()], "language")?.key).toBe("language");
    expect(shownCategory([status(), language()], null)).toBeNull();
    expect(shownCategory([status(), language()], "gone")).toBeNull();
    expect(shownCategory([status()], null)?.key).toBe("status");
  });
});

describe("FilterPopoverPanel", () => {
  test("lists the categories and prompts for one until it is chosen", () => {
    const html = panel([status(), language()], null);
    expect(html).toContain("Status");
    expect(html).toContain("Language");
    expect(html).toContain("Select a field");
    expect(html).not.toContain('type="checkbox"');
  });

  test("a multi select shows checkboxes with the chosen values ticked and its count", () => {
    const html = panel([status(["DONE"]), language()], "status");
    expect(html.match(/type="checkbox"/g)).toHaveLength(2);
    expect(html).toMatch(/type="checkbox"[^>]*checked=""[^>]*\/?>(?:(?!<label).)*Done/);
    expect(html).toContain('aria-selected="true" data-category="status"');
    expect(html).not.toContain("Search options");
  });

  test("a single select shows one radio group with a search box", () => {
    const html = panel([language("nl")], null, "dut");
    expect(html.match(/type="radio"/g)).toHaveLength(1);
    expect(html).toContain("Dutch");
    expect(html).not.toContain("English");
    expect(html).toContain('placeholder="Search options"');
    expect(html).toContain('value="dut"');
  });

  test("an empty list shows the category's empty text or the default one", () => {
    expect(panel([language()], null, "xyz")).toContain("No options");
    expect(panel([relation({ options: [], emptyLabel: "No relations" })], null)).toContain(
      "No relations"
    );
  });

  test("a loading server search shows the spinner instead of the empty text", () => {
    const html = panel([relation({ options: [], isLoading: true })], null);
    expect(html).toContain('role="status"');
    expect(html).toContain("Loading");
    expect(html).not.toContain("No options");
  });

  test("a failed server search shows its message", () => {
    const html = panel([relation({ error: "Relations could not load" })], null);
    expect(html).toContain('role="alert"');
    expect(html).toContain("Relations could not load");
  });

  test("the Clear footer shows only while a filter is set", () => {
    expect(panel([status(), language()], null)).not.toContain("Clear all");
    expect(panel([status(), language("en")], null)).toContain("Clear all");
  });
});

describe("FilterPopover", () => {
  function trigger(categories: FilterCategory[]): string {
    return renderToStaticMarkup(
      <FilterPopover categories={categories} onClear={() => undefined} labels={LABELS} />
    );
  }

  test("the trigger is named and has no badge while nothing is set", () => {
    const html = trigger([status(), language()]);
    expect(html).toContain('<span class="sr-only">Filter</span>');
    expect(html).not.toContain("data-active");
    expect(html).not.toContain("tabular-nums");
  });

  test("the trigger badges the number of chosen values", () => {
    const html = trigger([status(["OPEN", "DONE"]), language("en")]);
    expect(html).toContain('data-active="true"');
    expect(html).toMatch(/tabular-nums">3<\/span>/);
  });
});
