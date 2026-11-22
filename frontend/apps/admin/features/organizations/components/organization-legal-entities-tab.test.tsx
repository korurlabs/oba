import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";
import type { LegalEntityListItem } from "../schemas";

// The tab reads the list through a query hook. The hooks, the table and the
// form dialog are replaced: the table prints its rows and the column options
// are recorded, so the test reads what the role gating passed down and can run
// a delete.
type ListResult = {
  data?: { data: LegalEntityListItem[] };
  isLoading: boolean;
  isError: boolean;
  refetch: () => void;
};
type ColumnOptions = { canEdit: boolean; onDelete: (legalEntityId: string) => void };

let list: ListResult;
let columnOptions: ColumnOptions;
const deleted: string[] = [];

const actualIntl = { ...(await import("next-intl")) };
const actualHooks = { ...(await import("../hooks/use-legal-entities")) };
const actualColumns = { ...(await import("../hooks/use-legal-entity-columns")) };
const actualTable = { ...(await import("@/components/data-table")) };
const actualDialog = { ...(await import("./legal-entity-form-dialog")) };
const actualLoadError = { ...(await import("./organization-load-error")) };

mock.module("next-intl", () => ({
  ...actualIntl,
  useTranslations: () => (key: string) => key,
}));
mock.module("../hooks/use-legal-entities", () => ({
  ...actualHooks,
  useLegalEntities: () => list,
  useDeleteLegalEntity: () => ({
    isPending: false,
    mutate: (legalEntityId: string) => void deleted.push(legalEntityId),
  }),
}));
mock.module("../hooks/use-legal-entity-columns", () => ({
  ...actualColumns,
  useLegalEntityColumns: (options: ColumnOptions) => {
    columnOptions = options;
    return [];
  },
}));
mock.module("@/components/data-table", () => ({
  ...actualTable,
  DataTable: ({
    data,
    emptyMessage,
  }: {
    data: LegalEntityListItem[];
    emptyMessage: React.ReactNode;
  }) =>
    data.length === 0 ? (
      <>{emptyMessage}</>
    ) : (
      <p>{`rows:${data.map((row) => row.shortName).join(",")}`}</p>
    ),
}));
mock.module("./legal-entity-form-dialog", () => ({
  ...actualDialog,
  LegalEntityFormDialog: () => <p>form-dialog</p>,
}));
mock.module("./organization-load-error", () => ({
  ...actualLoadError,
  OrganizationLoadError: () => <p>load-error</p>,
}));

afterAll(() => {
  mock.module("next-intl", () => actualIntl);
  mock.module("../hooks/use-legal-entities", () => actualHooks);
  mock.module("../hooks/use-legal-entity-columns", () => actualColumns);
  mock.module("@/components/data-table", () => actualTable);
  mock.module("./legal-entity-form-dialog", () => actualDialog);
  mock.module("./organization-load-error", () => actualLoadError);
});

const { LegalEntitiesTab } = await import("./organization-legal-entities-tab");

const entity: LegalEntityListItem = {
  id: "le-1",
  shortName: "Korur NL",
  legalName: "Korur B.V.",
  country: "NLD",
  registrationNumber: null,
  updatedAt: "2026-11-21T09:00:00Z",
};

function render(canEdit: boolean): string {
  return renderToStaticMarkup(<LegalEntitiesTab organizationId="org-1" canEdit={canEdit} />);
}

beforeEach(() => {
  list = { data: { data: [entity] }, isLoading: false, isError: false, refetch: () => undefined };
  deleted.length = 0;
});

describe("LegalEntitiesTab", () => {
  test("lists the legal entities for every role", () => {
    expect(render(false)).toContain("rows:Korur NL");
    expect(render(true)).toContain("rows:Korur NL");
  });

  test("an OWNER or ADMIN gets the New button, the row actions and the form dialog", () => {
    const html = render(true);

    expect(html).toContain("legalEntities.newLegalEntity");
    expect(html).toContain("form-dialog");
    expect(columnOptions.canEdit).toBe(true);
  });

  test("a MEMBER or GUEST reads only: no New button, no row actions, no dialog", () => {
    const html = render(false);

    expect(html).not.toContain("legalEntities.newLegalEntity");
    expect(html).not.toContain("form-dialog");
    expect(columnOptions.canEdit).toBe(false);
  });

  test("an empty list tells a reader who adds legal entities", () => {
    list = { data: { data: [] }, isLoading: false, isError: false, refetch: () => undefined };

    expect(render(false)).toContain("legalEntities.noLegalEntitiesReadOnly");
    expect(render(true)).toContain("legalEntities.noLegalEntitiesDescription");
  });

  test("a failed first load shows the retry error in place of the table", () => {
    list = { isLoading: false, isError: true, refetch: () => undefined };

    const html = render(true);

    expect(html).toContain("load-error");
    expect(html).not.toContain("rows:");
  });

  test("a failed refetch keeps the cached rows", () => {
    list = { ...list, isError: true };

    expect(render(true)).toContain("rows:Korur NL");
  });

  test("a confirmed delete runs the delete mutation with the id", () => {
    render(true);

    columnOptions.onDelete("le-1");

    expect(deleted).toEqual(["le-1"]);
  });
});
