import { describe, expect, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";
import { TicketSectionHeader } from "./ticket-section-header";

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
