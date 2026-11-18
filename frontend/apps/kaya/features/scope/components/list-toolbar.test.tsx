import { afterAll, describe, expect, mock, test } from "bun:test";
import { Children, isValidElement, type ReactElement, type ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";

// The filter menu lives in a portal that a static render leaves out, so the
// popover shows its content in place; the toolbar itself is real.
const actualPopover = { ...(await import("@oba/ui/sky/popover")) };
mock.module("@oba/ui/sky/popover", () => ({
  ...actualPopover,
  Popover: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  PopoverTrigger: ({ children }: { children: ReactNode }) => <>{children}</>,
  PopoverContent: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));
afterAll(() => {
  mock.module("@oba/ui/sky/popover", () => actualPopover);
});

const { ListToolbar, NewButton } = await import("./list-toolbar");

type Props = Record<string, unknown> & { children?: ReactNode };

/** Walks an element tree, rendering only the filter menu (the one hook-free component that holds the checkbox), and collects the host elements of `tag`. */
function hostElements(node: ReactNode, tag: string): ReactElement<Props>[] {
  if (!isValidElement<Props>(node)) return [];
  const found: ReactElement<Props>[] = [];
  if (node.type === tag) found.push(node);
  if (typeof node.type === "function" && node.type.name === "FilterMenu") {
    const rendered = (node.type as (props: Props) => ReactNode)(node.props);
    return [...found, ...hostElements(rendered, tag)];
  }
  Children.forEach(node.props.children, (child: ReactNode) => {
    found.push(...hostElements(child, tag));
  });
  return found;
}

const noop = () => undefined;
const SEARCH = { value: "", onChange: noop, label: "Search skills", placeholder: "Search" };

describe("ListToolbar filter", () => {
  test("the menu holds a Show inherited checkbox that mirrors the filter", () => {
    const on = renderToStaticMarkup(<ListToolbar filter={{ shown: true, onChange: noop }} />);
    expect(on).toContain("Show inherited");
    expect(on).toMatch(/<input[^>]*type="checkbox"[^>]*checked=""/);
    expect(on).toContain('aria-label="What is inherited?"');
    const off = renderToStaticMarkup(<ListToolbar filter={{ shown: false, onChange: noop }} />);
    expect(off).not.toMatch(/<input[^>]*type="checkbox"[^>]*checked=""/);
  });

  test("ticking the checkbox hands the new state to the filter", () => {
    const changes: boolean[] = [];
    const toolbar = ListToolbar({
      filter: { shown: false, onChange: (shown: boolean) => changes.push(shown) },
    });
    const [checkbox] = hostElements(toolbar, "input");
    const onChange = checkbox?.props.onChange as (event: { target: { checked: boolean } }) => void;
    onChange({ target: { checked: true } });
    onChange({ target: { checked: false } });
    expect(changes).toEqual([true, false]);
  });

  test("the icon looks active only while inherited items are shown", () => {
    const on = renderToStaticMarkup(<ListToolbar filter={{ shown: true, onChange: noop }} />);
    expect(on).toContain('aria-label="Filter, inherited items shown"');
    expect(on).toContain('data-active="true"');
    const off = renderToStaticMarkup(<ListToolbar filter={{ shown: false, onChange: noop }} />);
    expect(off).toContain('aria-label="Filter"');
    expect(off).not.toContain("data-active");
  });

  test("no filter, as on a repository, hides the icon and the menu", () => {
    for (const filter of [null, undefined]) {
      const html = renderToStaticMarkup(<ListToolbar filter={filter} search={SEARCH} />);
      expect(html).not.toContain('aria-label="Filter');
      expect(html).not.toContain("Show inherited");
      expect(html).toContain('aria-label="Search skills"');
    }
  });
});

describe("ListToolbar search, tabs and actions", () => {
  test("the search shows as a collapsed button, and a typed term keeps the field open", () => {
    const closed = renderToStaticMarkup(<ListToolbar search={SEARCH} />);
    expect(closed).toContain('aria-label="Search skills"');
    expect(closed).not.toContain('type="search"');
    const typed = renderToStaticMarkup(<ListToolbar search={{ ...SEARCH, value: "kai" }} />);
    expect(typed).toContain('value="kai"');
  });

  test("without a search no search button renders", () => {
    expect(renderToStaticMarkup(<ListToolbar />)).not.toContain("Search");
  });

  test("tabs come before the right side, actions before the create button", () => {
    const html = renderToStaticMarkup(
      <ListToolbar
        tabs={<span>TABS</span>}
        search={SEARCH}
        actions={<span>ACTIONS</span>}
        create={<span>CREATE</span>}
      />
    );
    const order = ["TABS", 'aria-label="Search skills"', "ACTIONS", "CREATE"].map((part: string) =>
      html.indexOf(part)
    );
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
