import { afterAll, describe, expect, mock, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";
import { NextIntlClientProvider } from "next-intl";
import en from "@/locales/en.json";

// The locale-aware link needs a router; a plain anchor keeps the href and classes visible.
const actualNavigation = { ...(await import("@oba/shared/libs/i18n/navigation")) };
mock.module("@oba/shared/libs/i18n/navigation", () => ({
  ...actualNavigation,
  Link: ({ children, ...props }: React.ComponentProps<"a">) => <a {...props}>{children}</a>,
}));
afterAll(() => {
  mock.module("@oba/shared/libs/i18n/navigation", () => actualNavigation);
});

const { CyberscoreBreakdown } = await import("./cyberscore-breakdown");

function markup(breakdown: Parameters<typeof CyberscoreBreakdown>[0]["breakdown"]): string {
  return renderToStaticMarkup(
    <NextIntlClientProvider locale="en" messages={en} timeZone="UTC">
      <CyberscoreBreakdown breakdown={breakdown} />
    </NextIntlClientProvider>
  );
}

describe("CyberscoreBreakdown", () => {
  test("each category row links to the checks of that category, in display order", () => {
    const html = markup({ DATA: 8, IDENTITY: 23 });
    const hrefs = [...html.matchAll(/<a [^>]*href="([^"]*)"/g)].map((match) => match[1]);
    expect(hrefs).toEqual([
      "/cyberscore/checks?category=IDENTITY",
      "/cyberscore/checks?category=DATA",
    ]);
  });

  test("a row names its category, score and level, hovers like a ticket card and shows focus", () => {
    const html = markup({ IDENTITY: 23 });
    const link = html.match(/<a [^>]*>/)?.[0] ?? "";
    expect(link).toContain(
      `aria-label="${en.cyberscore.categories.IDENTITY}: 23 percent, ${en.cyberscore.levels.HIGH}. Show its checks."`
    );
    expect(link).toContain("kazan-card");
    expect(link).toContain("hover:border-primary/50");
    expect(link).toContain("focus-visible:ring-2");
    expect(html).toContain("23%");
  });

  test("without a scorable category shows the empty text and no link", () => {
    const html = markup({});
    expect(html).toContain(en.cyberscore.breakdown.empty);
    expect(html).not.toContain("<a ");
  });
});
