import { describe, expect, test } from "bun:test";
import type { CSSProperties, MouseEvent, ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";

import {
  handleNavClick,
  SkyAppShell,
  SkySidebar,
  SkyTopBar,
  type SkyLinkProps,
  type SkyNavItem,
} from "./app-shell";
import { SidebarProvider } from "./sidebar";

function Link({ href, children, ...rest }: SkyLinkProps) {
  return (
    <a href={href} {...rest}>
      {children}
    </a>
  );
}

const Icon = () => <svg />;

function item(label: string, href: string): SkyNavItem {
  return { label, href, icon: Icon };
}

function markup(props: Partial<Parameters<typeof SkySidebar>[0]>, children?: ReactNode): string {
  return renderToStaticMarkup(
    <SidebarProvider>
      <SkySidebar pathname="/" linkComponent={Link} {...props}>
        {children}
      </SkySidebar>
    </SidebarProvider>
  );
}

describe("SkySidebar", () => {
  test("renders its children after the sections, inside the scrolling content", () => {
    const html = markup(
      { sections: [{ label: "Pages", items: [item("Overview", "/overview")] }] },
      <nav data-tree="">The tree</nav>
    );
    expect(html).toContain("Overview");
    expect(html).toContain('data-tree=""');
    expect(html.indexOf("Overview")).toBeLessThan(html.indexOf('data-tree=""'));
    const content = html.indexOf('data-sidebar="content"');
    expect(content).toBeGreaterThan(-1);
    expect(content).toBeLessThan(html.indexOf('data-tree=""'));
  });

  test("renders children alone when the app passes no sections", () => {
    const html = markup({}, <nav data-tree="">The tree</nav>);
    expect(html).toContain("The tree");
    expect(html).not.toContain('data-sidebar="group-label"');
  });

  test("renders every footer entry, in order, below the content", () => {
    const html = markup({
      footerItems: [
        item("Recent", "/recent"),
        item("Trash", "/trash"),
        item("Settings", "/settings"),
      ],
    });
    const footer = html.slice(html.indexOf('data-sidebar="footer"'));
    for (const label of ["Recent", "Trash", "Settings"]) expect(footer).toContain(label);
    expect(footer.indexOf("Recent")).toBeLessThan(footer.indexOf("Trash"));
    expect(footer.indexOf("Trash")).toBeLessThan(footer.indexOf("Settings"));
    expect(footer).toContain('href="/recent"');
    expect(footer).toContain('href="/settings"');
  });

  test("marks the footer entry whose page is in view", () => {
    const html = markup({
      pathname: "/trash",
      footerItems: [item("Recent", "/recent"), item("Trash", "/trash")],
    });
    expect(html.match(/data-active="true"/g)).toHaveLength(1);
    expect(html.indexOf('data-active="true"')).toBeGreaterThan(html.indexOf('href="/recent"'));
  });

  test("shows the footer mark with the footer entries", () => {
    const html = markup({
      footerItems: [item("Settings", "/settings")],
      footerMark: <i data-mark="" />,
    });
    expect(html).toContain('data-mark=""');
  });

  test("draws no footer, divider or mark without footer entries", () => {
    const html = markup({ footerItems: [], footerMark: <i data-mark="" /> });
    expect(html).not.toContain('data-sidebar="footer"');
    expect(html).not.toContain('data-mark=""');
    expect(html).not.toContain("sky-lattice");
  });

  test("renders without sections, children or footer entries", () => {
    const html = markup({});
    expect(html).toContain('data-sidebar="content"');
  });

  test("draws the rail at the panel's edge by default", () => {
    const html = markup({});
    expect(html).toContain('data-sidebar="rail"');
  });

  test("draws the given edge in place of the rail", () => {
    const html = markup({ edge: <span data-edge="" /> });
    expect(html).toContain('data-edge=""');
    expect(html).not.toContain('data-sidebar="rail"');
  });
});

function topBarToggle(menuToggle?: "mobile" | "always"): string {
  const html = renderToStaticMarkup(
    <SidebarProvider>
      <SkyTopBar logo="Logo" homeLabel="Home" linkComponent={Link} menuToggle={menuToggle} />
    </SidebarProvider>
  );
  const trigger = html.match(/<button[^>]*data-sidebar="trigger"[^>]*>/);
  expect(trigger).not.toBeNull();
  return trigger?.[0] ?? "";
}

describe("SkyTopBar", () => {
  test("shows the menu toggle only on narrow windows by default", () => {
    expect(topBarToggle()).toContain("md:hidden");
    expect(topBarToggle("mobile")).toContain("md:hidden");
  });

  test("shows the menu toggle on every width when asked to", () => {
    expect(topBarToggle("always")).not.toContain("md:hidden");
  });
});

function shell(props: Partial<Parameters<typeof SkyAppShell>[0]>): string {
  return renderToStaticMarkup(
    <SkyAppShell logo="Logo" homeLabel="Home" pathname="/" linkComponent={Link} {...props}>
      <p data-page="">The page</p>
    </SkyAppShell>
  );
}

describe("SkyAppShell", () => {
  test("puts the page in a centred column by default", () => {
    const html = shell({});
    expect(html).toContain("max-w-7xl");
    expect(html).toContain("overflow-auto");
    expect(html).toContain('data-page=""');
  });

  test("lets the page fill the space beside the menu with fill", () => {
    const html = shell({ fill: true });
    expect(html).not.toContain("max-w-7xl");
    expect(html).toMatch(/<main class="[^"]*flex-col[^"]*overflow-hidden[^"]*"><p data-page="">/);
  });

  test("passes the provider style and classes to the sidebar wrapper", () => {
    const html = shell({
      providerStyle: { "--sidebar-width": "280px" } as CSSProperties,
      providerClassName: "cursor-col-resize",
    });
    const wrapper = html.match(/<div[^>]*data-slot="sidebar-wrapper"[^>]*>/)?.[0] ?? "";
    expect(wrapper).toContain("--sidebar-width:280px");
    expect(wrapper).toContain("cursor-col-resize");
  });

  test("passes the menu toggle and the edge through", () => {
    const html = shell({ menuToggle: "always", edge: <span data-edge="" /> });
    expect(html).toContain('data-edge=""');
    expect(html).not.toContain('data-sidebar="rail"');
    expect(html).not.toContain("md:hidden");
  });
});

function click(prevent: boolean): { closed: boolean; handled: boolean } {
  const state = { closed: false, handled: false, prevented: false };
  const event = {
    preventDefault: () => {
      state.prevented = true;
    },
    get defaultPrevented() {
      return state.prevented;
    },
  } as unknown as MouseEvent<HTMLAnchorElement>;
  handleNavClick(
    event,
    (clicked: MouseEvent<HTMLAnchorElement>) => {
      state.handled = true;
      if (prevent) clicked.preventDefault();
    },
    () => {
      state.closed = true;
    }
  );
  return { closed: state.closed, handled: state.handled };
}

describe("handleNavClick", () => {
  test("runs the app's handler and closes the menu sheet", () => {
    expect(click(false)).toEqual({ closed: true, handled: true });
  });

  test("keeps the sheet open when the app's handler keeps the page", () => {
    expect(click(true)).toEqual({ closed: false, handled: true });
  });

  test("closes the sheet when the app passes no handler", () => {
    let closed = false;
    handleNavClick({ defaultPrevented: false } as MouseEvent<HTMLAnchorElement>, undefined, () => {
      closed = true;
    });
    expect(closed).toBe(true);
  });
});
