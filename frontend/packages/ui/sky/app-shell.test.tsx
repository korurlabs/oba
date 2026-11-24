import { describe, expect, test } from "bun:test";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";

import { SkySidebar, type SkyLinkProps, type SkyNavItem } from "./app-shell";
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
});
