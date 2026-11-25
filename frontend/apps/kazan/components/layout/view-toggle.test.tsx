import { describe, expect, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";
import { NextIntlClientProvider } from "next-intl";
import en from "@/locales/en.json";
import { ViewToggle, type AnyViewMode } from "./view-toggle";

function labels(element: React.ReactElement): string[] {
  const html = renderToStaticMarkup(
    <NextIntlClientProvider locale="en" messages={en} timeZone="UTC">
      {element}
    </NextIntlClientProvider>
  );
  return [...html.matchAll(/<button[^>]*aria-label="([^"]*)"/g)].map((match) => match[1] ?? "");
}

describe("ViewToggle", () => {
  test("without modes it offers list and kanban, as on the ticket pages", () => {
    expect(labels(<ViewToggle view="list" onViewChange={() => undefined} />)).toEqual([
      en.views.switchToList,
      en.views.switchToKanban,
    ]);
  });

  test("offers the given modes in their order and marks the current one", () => {
    const html = renderToStaticMarkup(
      <NextIntlClientProvider locale="en" messages={en} timeZone="UTC">
        <ViewToggle<AnyViewMode>
          view="kanban"
          onViewChange={() => undefined}
          modes={["cards", "list", "kanban"]}
        />
      </NextIntlClientProvider>
    );
    const buttons = [...html.matchAll(/aria-label="([^"]*)" aria-pressed="(true|false)"/g)].map(
      (match) => [match[1], match[2]]
    );
    expect(buttons).toEqual([
      [en.views.switchToCards, "false"],
      [en.views.switchToList, "false"],
      [en.views.switchToKanban, "true"],
    ]);
    expect(html).toContain(`>${en.views.cards}<`);
  });
});
