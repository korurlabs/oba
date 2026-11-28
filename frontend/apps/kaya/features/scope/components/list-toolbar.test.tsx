import { describe, expect, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";
import { FilterPopoverPanel } from "@oba/ui/sky/filter-popover";

const { INHERITED_SHOWN, ListToolbar, NewButton, inheritedCategory } =
  await import("./list-toolbar");

const noop = () => undefined;
const SEARCH = { value: "", onChange: noop, label: "Search skills", placeholder: "Search" };

/** The popover's content, which a static render of the toolbar leaves in its portal. */
function panel(shown: boolean): string {
  return renderToStaticMarkup(
    <FilterPopoverPanel
      categories={[inheritedCategory({ shown, onChange: noop })]}
      selectedKey={null}
      onSelectCategory={noop}
      search=""
      onSearchChange={noop}
      onClear={noop}
      labels={{
        trigger: "Filter",
        chooseCategory: "Select a field",
        searchOptions: "Search options",
        noOptions: "No options",
        loading: "Loading",
        clear: "Clear all",
      }}
    />
  );
}

describe("ListToolbar filter", () => {
  test("Show inherited is the one option of a single select Inherited category", () => {
    const category = inheritedCategory({ shown: true, onChange: noop });
    expect(category.mode).toBe("single");
    expect(category.label).toBe("Inherited");
    expect(category.options.map((option) => option.label)).toEqual(["Show inherited"]);
    expect(category.value).toBe(INHERITED_SHOWN);
    expect(inheritedCategory({ shown: false, onChange: noop }).value).toBeNull();
  });

  test("choosing or clearing the option hands the boolean to the filter", () => {
    const changes: boolean[] = [];
    const category = inheritedCategory({
      shown: false,
      onChange: (shown: boolean) => changes.push(shown),
    });
    if (category.mode !== "single") throw new Error("expected a single select");
    category.onChange(INHERITED_SHOWN);
    category.onChange(null);
    expect(changes).toEqual([true, false]);
  });

  test("the panel shows the option ticked while inherited items are listed", () => {
    const on = panel(true);
    expect(on).toContain("Show inherited");
    expect(on).toMatch(/<input[^>]*type="radio"[^>]*checked=""/);
    expect(on).toContain("Clear all");
    const off = panel(false);
    expect(off).not.toMatch(/<input[^>]*type="radio"[^>]*checked=""/);
    expect(off).not.toContain("Clear all");
  });

  test("the Filter button is badged and active only while inherited items are shown", () => {
    const on = renderToStaticMarkup(<ListToolbar filter={{ shown: true, onChange: noop }} />);
    expect(on).toContain('title="Filter"');
    expect(on).toContain('data-active="true"');
    expect(on).toContain(">1</span>");
    const off = renderToStaticMarkup(<ListToolbar filter={{ shown: false, onChange: noop }} />);
    expect(off).toContain('title="Filter"');
    expect(off).not.toContain("data-active");
  });

  test("no filter, as on a repository, hides the Filter button", () => {
    for (const filter of [null, undefined]) {
      const html = renderToStaticMarkup(<ListToolbar filter={filter} search={SEARCH} />);
      expect(html).not.toContain('title="Filter"');
      expect(html).toContain('aria-label="Search skills"');
    }
  });
});

describe("ListToolbar search, tabs and actions", () => {
  test("the search is the sky search field, open with its placeholder and the searched term", () => {
    const empty = renderToStaticMarkup(<ListToolbar search={SEARCH} />);
    expect(empty).toContain('aria-label="Search skills"');
    expect(empty).toContain('placeholder="Search"');
    const typed = renderToStaticMarkup(<ListToolbar search={{ ...SEARCH, value: "kai" }} />);
    expect(typed).toContain('value="kai"');
    expect(typed).toContain('aria-label="Clear search"');
  });

  test("without a search no search button renders", () => {
    expect(renderToStaticMarkup(<ListToolbar />)).not.toContain("Search");
  });

  test("search first, then tabs, then actions, Filter and create, as on every sky list", () => {
    const html = renderToStaticMarkup(
      <ListToolbar
        tabs={<span>TABS</span>}
        search={SEARCH}
        filter={{ shown: false, onChange: noop }}
        actions={<span>ACTIONS</span>}
        create={<span>CREATE</span>}
      />
    );
    const order = [
      'aria-label="Search skills"',
      "TABS",
      'aria-hidden="true"></div>',
      "ACTIONS",
      'title="Filter"',
      "CREATE",
    ].map((part: string) => html.indexOf(part));
    expect(order.every((index: number) => index >= 0)).toBe(true);
    expect(order).toEqual([...order].sort((left: number, right: number) => left - right));
  });

  test("a null create renders no button, as on a repository", () => {
    expect(renderToStaticMarkup(<ListToolbar create={null} search={SEARCH} />)).not.toContain(
      "New"
    );
  });
});

describe("NewButton", () => {
  test("unlocked, it is an enabled primary button with the label", () => {
    const html = renderToStaticMarkup(<NewButton label="New skill" />);
    expect(html).toContain("bg-primary");
    expect(html).toContain("New skill</button>");
    expect(html).not.toContain('disabled=""');
    expect(html).not.toContain("Why is this disabled?");
  });

  test("locked by another pack's change, it is disabled and the lock tip sits beside it", () => {
    const html = renderToStaticMarkup(<NewButton label="New skill" lockedBy="org" />);
    expect(html).toMatch(/<button[^>]*disabled=""[^>]*>[^]*?New skill<\/button>/);
    expect(html).toContain('aria-label="Why is this disabled?"');
  });

  test("a locked button ignores its other props, so no trigger can open it", () => {
    const html = renderToStaticMarkup(
      <NewButton label="New skill" lockedBy="org" data-slot="dialog-trigger" />
    );
    expect(html).not.toContain("dialog-trigger");
  });

  test("help is kept off the markup until the tooltip opens", () => {
    const html = renderToStaticMarkup(<NewButton label="New guardrail" help="A guardrail is" />);
    expect(html).toContain("New guardrail</button>");
    expect(html).not.toContain("A guardrail is");
  });
});
