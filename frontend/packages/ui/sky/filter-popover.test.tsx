import { describe, expect, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

import {
  FilterPopover,
  FilterPopoverPanel,
  categoryActiveCount,
  groupOptions,
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

function period(value: string = "upcoming"): SingleFilterCategory {
  return {
    key: "period",
    label: "Period",
    mode: "single",
    value,
    defaultValue: "upcoming",
    onChange: () => undefined,
    options: [
      { value: "upcoming", label: "Upcoming" },
      { value: "past", label: "Past" },
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

function stages(value: string[] = []): MultiFilterCategory {
  return {
    key: "stage",
    label: "Stage",
    mode: "multi",
    value,
    onChange: () => undefined,
    searchable: true,
    options: [
      { value: "NEW", label: "New", group: "Lead" },
      { value: "QUALIFIED", label: "Qualified", group: "Lead" },
      { value: "ACTIVE", label: "Active", group: "Customer" },
    ],
  };
}

describe("active counts", () => {
  test("a multi select counts each value, a single select at most one", () => {
    expect(categoryActiveCount(status())).toBe(0);
    expect(categoryActiveCount(status(["OPEN", "DONE"]))).toBe(2);
    expect(categoryActiveCount(language())).toBe(0);
    expect(categoryActiveCount(language("nl"))).toBe(1);
    expect(totalActiveCount([status(["OPEN", "DONE"]), language("nl")])).toBe(3);
  });

  test("a single select with a default does not count its default", () => {
    expect(categoryActiveCount(period())).toBe(0);
    expect(categoryActiveCount(period("past"))).toBe(1);
    expect(totalActiveCount([period(), language("nl")])).toBe(1);
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

  test("a single select with a default returns to it when the chosen option is clicked again", () => {
    expect(nextSingleValue("upcoming", "past", "upcoming")).toBe("past");
    expect(nextSingleValue("past", "past", "upcoming")).toBe("upcoming");
    expect(nextSingleValue("upcoming", "upcoming", "upcoming")).toBe("upcoming");
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

  test("categories and options are sky glass rows, with no grid frame", () => {
    const html = panel([status(["DONE"]), language()], "status");
    expect(html.match(/sky-glass-item/g)).toHaveLength(4);
    expect(html).toContain("sky-glass-divider");
    expect(html).not.toContain("grid-frame");
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

  test("a category on its default ticks it, but shows no count and no Clear footer", () => {
    const html = panel([period()], null);
    expect(html.match(/type="radio"/g)).toHaveLength(2);
    expect(html).toMatch(/type="radio"[^>]*checked=""[^>]*\/?>(?:(?!<label).)*Upcoming/);
    expect(html).not.toContain("tabular-nums");
    expect(html).not.toContain("Clear all");
  });

  test("a category off its default counts one and offers Clear", () => {
    const html = panel([period("past")], null);
    expect(html).toMatch(/tabular-nums">1<\/span>/);
    expect(html).toContain("Clear all");
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

  test("the trigger has no badge while a defaulted category is on its default", () => {
    expect(trigger([period()])).not.toContain("data-active");
    const html = trigger([period("past")]);
    expect(html).toContain('data-active="true"');
    expect(html).toMatch(/tabular-nums">1<\/span>/);
  });
});

describe("option groups", () => {
  test("splits options into runs of one group, in their order", () => {
    const groups = groupOptions([
      { value: "a", label: "A" },
      { value: "b", label: "B", group: "Lead" },
      { value: "c", label: "C", group: "Lead" },
      { value: "d", label: "D", group: "Customer" },
    ]);
    expect(groups.map((group) => group.heading)).toEqual([null, "Lead", "Customer"]);
    expect(groups[1]?.options.map((option) => option.value)).toEqual(["b", "c"]);
    expect(groupOptions([])).toEqual([]);
  });

  test("options without a group render no heading", () => {
    const html = panel([status()], null);
    expect(html).not.toContain('role="group"');
    expect(html).not.toContain("data-option-group");
  });

  test("each group shows its heading once, labelling the group, above its options", () => {
    const html = panel([stages(["ACTIVE"])], null);
    expect(html.match(/role="group"/g)).toHaveLength(2);
    expect(html.match(/>Lead</g)).toHaveLength(1);
    const order = [">Lead<", "New", "Qualified", ">Customer<", "Active"].map((part) =>
      html.indexOf(part)
    );
    for (const index of order) expect(index).toBeGreaterThan(-1);
    expect([...order].sort((a, b) => a - b)).toEqual(order);
    const labelledBy = html.match(/aria-labelledby="([^"]+)"/)?.[1];
    expect(labelledBy).toBeDefined();
    expect(html).toContain(`id="${labelledBy}"`);
  });

  test("the option search matches a group's heading and drops emptied groups", () => {
    expect(visibleOptions(stages(), "customer").map((option) => option.value)).toEqual(["ACTIVE"]);
    const html = panel([stages()], null, "qual");
    expect(html).toContain(">Lead<");
    expect(html).not.toContain(">Customer<");
  });
});
