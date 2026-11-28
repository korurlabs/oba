import { describe, expect, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

import { EmptyState, ListEmptyState, type ListEmptyStateLabels } from "./page-states";

const LABELS: ListEmptyStateLabels = {
  emptyTitle: "No campaigns yet",
  emptyDescription: "Create the first campaign.",
  filteredTitle: "No campaign matches",
  filteredDescription: "Change the search or clear the filters.",
  clearFilters: "Clear filters",
};

function list(filtered: boolean, create?: React.ReactNode): string {
  return renderToStaticMarkup(
    <ListEmptyState
      filtered={filtered}
      onClearFilters={() => undefined}
      create={create}
      labels={LABELS}
    />
  );
}

describe("EmptyState", () => {
  test("shows its title and text without an action", () => {
    const html = renderToStaticMarkup(<EmptyState title="Nothing" description="Here yet" />);
    expect(html).toContain("Nothing");
    expect(html).toContain("Here yet");
    expect(html).not.toContain('data-slot="card-content"');
  });

  test("shows the action below its text", () => {
    const html = renderToStaticMarkup(
      <EmptyState title="Nothing" description="Here yet" action={<button>ADD</button>} />
    );
    expect(html).toContain('data-slot="card-content"');
    expect(html.indexOf("Here yet")).toBeLessThan(html.indexOf("ADD"));
  });
});

describe("ListEmptyState", () => {
  test("an empty list offers the create button", () => {
    const html = list(false, <button>CREATE</button>);
    expect(html).toContain("No campaigns yet");
    expect(html).toContain("Create the first campaign.");
    expect(html).toContain("CREATE");
    expect(html).not.toContain("Clear filters");
  });

  test("a read only empty list shows no action", () => {
    const html = list(false);
    expect(html).toContain("No campaigns yet");
    expect(html).not.toContain("<button");
  });

  test("a filtered list offers clearing the filters, not the create button", () => {
    const html = list(true, <button>CREATE</button>);
    expect(html).toContain("No campaign matches");
    expect(html).toContain("Change the search or clear the filters.");
    expect(html).toContain("Clear filters");
    expect(html).not.toContain("CREATE");
    expect(html).not.toContain("No campaigns yet");
  });
});
