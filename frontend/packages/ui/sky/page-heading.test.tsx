import { describe, expect, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

import type { SkyLinkProps } from "./app-shell";
import { PageHeading } from "./page-heading";

describe("PageHeading", () => {
  test("sets the title as the page's heading with its description and actions", () => {
    const html = renderToStaticMarkup(
      <PageHeading title="Campaigns" description="Every campaign." actions={<button>NEW</button>} />
    );
    expect(html).toContain('<h1 class="break-words">Campaigns</h1>');
    expect(html).toContain('<p class="text-muted-foreground">Every campaign.</p>');
    expect(html.indexOf("Campaigns")).toBeLessThan(html.indexOf("NEW"));
    expect(html).not.toContain("<a");
  });

  test("sets a file path title in monospace with the same description, actions and back link", () => {
    const html = renderToStaticMarkup(
      <PageHeading
        title=".agents/rules/core.md"
        titleVariant="mono"
        description="The rules shared by every repository."
        actions={<button>EDIT</button>}
        back={{ href: "/rules", label: "All rules" }}
      />
    );
    expect(html).toContain(
      '<h1 class="min-w-0 font-mono text-base break-all">.agents/rules/core.md</h1>'
    );
    expect(html).not.toContain("break-words");
    expect(html).toContain(
      '<p class="text-muted-foreground">The rules shared by every repository.</p>'
    );
    expect(html).toContain('class="flex min-w-0 flex-col gap-1"');
    expect(html).toContain('class="flex flex-col gap-6"');
    expect(html).toContain('href="/rules"');
    expect(html.indexOf("All rules")).toBeLessThan(html.indexOf("<h1"));
    expect(html.indexOf("core.md")).toBeLessThan(html.indexOf("EDIT"));
  });

  test("leaves out an empty description and the actions group without actions", () => {
    const html = renderToStaticMarkup(<PageHeading title="Campaigns" description="" />);
    expect(html).not.toContain("<p");
    expect(html).not.toContain("flex-wrap items-center gap-2");
  });

  test("sets a description node as given", () => {
    const html = renderToStaticMarkup(
      <PageHeading title="Acme" description={<span data-badges="row">LEAD</span>} />
    );
    expect(html).toContain('<span data-badges="row">LEAD</span>');
    expect(html).not.toContain("<p");
  });

  test("puts the back link above the title as a small ghost button with an arrow", () => {
    const html = renderToStaticMarkup(
      <PageHeading title="Acme" back={{ href: "/relations", label: "All relations" }} />
    );
    expect(html).toContain('href="/relations"');
    expect(html).toContain('data-variant="ghost"');
    expect(html).toContain('data-size="sm"');
    expect(html).toContain("lucide-arrow-left");
    expect(html.indexOf("All relations")).toBeLessThan(html.indexOf("<h1"));
  });

  test("renders the back link through the app's link component", () => {
    function LocaleLink({ href, ...props }: SkyLinkProps) {
      return <a href={`/nl${href}`} {...props} />;
    }
    const html = renderToStaticMarkup(
      <PageHeading
        title="Acme"
        back={{ href: "/relations", label: "All relations" }}
        linkComponent={LocaleLink}
      />
    );
    expect(html).toContain('href="/nl/relations"');
  });
});
