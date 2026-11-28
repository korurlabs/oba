import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";
import { NextIntlClientProvider } from "next-intl";
import en from "@/locales/en.json";
import type { TicketListResponse, TicketStatus } from "@/features/tickets/schemas";

let layout: "modern" | "classic" = "modern";

const actualAppearance = {
  ...(await import("@/features/settings/hooks/use-kazan-appearance")),
};
mock.module("@/features/settings/hooks/use-kazan-appearance", () => ({
  ...actualAppearance,
  useKazanAppearance: () => ({ layout, background: "grid" }),
}));
afterAll(() => {
  mock.module("@/features/settings/hooks/use-kazan-appearance", () => actualAppearance);
});

const { TicketSectionHeader } = await import("./ticket-section-header");

beforeEach(() => {
  layout = "modern";
});

describe("TicketSectionHeader", () => {
  test("names the section and offers its add action, without counts", () => {
    const html = renderToStaticMarkup(
      <TicketSectionHeader title="Backlog" onAddTicket={() => {}} addLabel="Add to Backlog" />
    );

    expect(html).toContain(">Backlog</h2>");
    expect(html).toContain("Add to Backlog");
    expect(html).not.toContain("·");
  });

  test("lets a control lead the strip in place of the title", () => {
    const html = renderToStaticMarkup(
      <TicketSectionHeader title="Sprint" leading={<span data-lead="">SPRINT 1</span>} />
    );

    expect(html).toContain('data-lead=""');
    expect(html).not.toContain("<h2");
  });

  test("shows no add button without a handler", () => {
    const html = renderToStaticMarkup(<TicketSectionHeader title="Backlog" addLabel="Add" />);

    expect(html).not.toContain("<button");
  });
});

describe("TicketSectionHeader classic layout", () => {
  const tickets = [
    { id: "a", status: "TODO" },
    { id: "b", status: "DONE" },
  ] as unknown as TicketListResponse[];

  const render = (withTickets: boolean) =>
    renderToStaticMarkup(
      <NextIntlClientProvider locale="en" timeZone="UTC" messages={en}>
        <TicketSectionHeader title="Backlog" tickets={withTickets ? tickets : undefined} />
      </NextIntlClientProvider>
    );

  test("counts the section and shows the status chips", () => {
    layout = "classic";
    const html = render(true);
    expect(html).toContain("· 2");
    expect(html).toContain(en.ticket.status.TODO);
  });

  const renderChips = (selectedStatuses: TicketStatus[] | undefined, errors: unknown[]) =>
    renderToStaticMarkup(
      <NextIntlClientProvider
        locale="en"
        timeZone="UTC"
        messages={en}
        onError={(error) => errors.push(error)}
      >
        <TicketSectionHeader
          title="Backlog"
          tickets={tickets}
          selectedStatuses={selectedStatuses}
          onStatusToggle={() => {}}
        />
      </NextIntlClientProvider>
    );

  test("names each filter chip with its status and count from the real messages", () => {
    layout = "classic";
    const errors: unknown[] = [];
    const html = renderChips(undefined, errors);

    expect(errors).toEqual([]);
    expect(html).toContain('aria-label="Filter by To Do (1)"');
    expect(html).toContain('aria-label="Filter by Done (1)"');
  });

  test("drops the count from the chip name while a status filter is active", () => {
    layout = "classic";
    const errors: unknown[] = [];
    const html = renderChips(["TODO"], errors);

    expect(errors).toEqual([]);
    expect(html).toContain('aria-label="Filter by To Do"');
    expect(html).toContain('aria-pressed="true"');
  });

  test("shows no chips in the modern layout, even with tickets", () => {
    const html = render(true);
    expect(html).not.toContain("·");
    expect(html).not.toContain(en.ticket.status.TODO);
  });
});
