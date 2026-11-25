import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";
import { NextIntlClientProvider } from "next-intl";
import en from "@/locales/en.json";
import type { Check } from "../schemas";

// Who the reader is and which checks come back are stand-ins; the list, its
// columns, the access rule and the messages are real.
let role: "OWNER" | "ADMIN" | "MEMBER";

const ORGANIZATION_ID = "org-1";
function makeCheck(id: string, name: string, category: Check["category"]): Check {
  return {
    id,
    name,
    description: null,
    expected: `${name} holds`,
    weight: 20,
    sortOrder: 0,
    category,
    outcome: "FAIL",
    status: "DONE",
    updatedAt: new Date("2026-11-25T08:30:00Z"),
  } as unknown as Check;
}

const mfaCheck = makeCheck("c1", "MFA on admin accounts", "IDENTITY");
let checks: Check[];
let filters: {
  status: string | null;
  category: string | null;
  search: string | null;
  view: "cards" | "list" | "kanban";
};

const actualGetMe = { ...(await import("@oba/shared/features/users/hooks/use-get-me")) };
const actualChecks = { ...(await import("../hooks/use-checks")) };
const actualNavigation = { ...(await import("@oba/shared/libs/i18n/navigation")) };
const actualNuqs = { ...(await import("nuqs")) };

const idleMutation = {
  isPending: false,
  variables: undefined,
  mutate: () => undefined,
  mutateAsync: () => Promise.resolve(),
};

mock.module("@oba/shared/features/users/hooks/use-get-me", () => ({
  ...actualGetMe,
  useGetMe: () => ({
    data: {
      data: {
        defaultOrganizationId: ORGANIZATION_ID,
        organizations: [{ id: ORGANIZATION_ID, name: "Org", role }],
      },
    },
  }),
}));
mock.module("../hooks/use-checks", () => ({
  ...actualChecks,
  useChecks: () => ({
    data: { data: checks },
    isPending: false,
    isError: false,
    isFetching: false,
    refetch: () => Promise.resolve(),
  }),
  useSetCheckOutcome: () => idleMutation,
  useSetCheckStatus: () => idleMutation,
  useCreateCheck: () => ({ isPending: false, mutate: () => undefined }),
  useUpdateCheck: () => ({ isPending: false, mutate: () => undefined }),
  useDeleteCheck: () => ({ isPending: false, mutate: () => undefined }),
}));
mock.module("@oba/shared/libs/i18n/navigation", () => ({
  ...actualNavigation,
  Link: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));
mock.module("nuqs", () => ({
  ...actualNuqs,
  useQueryStates: () => [filters, () => Promise.resolve(new URLSearchParams())],
}));

afterAll(() => {
  mock.module("@oba/shared/features/users/hooks/use-get-me", () => actualGetMe);
  mock.module("../hooks/use-checks", () => actualChecks);
  mock.module("@oba/shared/libs/i18n/navigation", () => actualNavigation);
  mock.module("nuqs", () => actualNuqs);
});

const { CheckList, filterChecks, trackCheckSave } = await import("./check-list");
const { CheckCard } = await import("./check-card");

function markup(): string {
  return renderToStaticMarkup(
    <NextIntlClientProvider locale="en" messages={en} timeZone="UTC">
      <CheckList />
    </NextIntlClientProvider>
  );
}

beforeEach(() => {
  role = "MEMBER";
  checks = [mfaCheck];
  filters = { status: null, category: null, search: null, view: "cards" };
});

describe("CheckList write controls", () => {
  test("a MEMBER sees the checks read-only", () => {
    const html = markup();
    expect(html).toContain("MFA on admin accounts");
    expect(html).toContain(en.cyberscore.checks.readOnly);
    expect(html).toContain(en.cyberscore.outcomes.FAIL);
    expect(html).not.toContain(`>${en.cyberscore.checks.add}<`);
    expect(html).not.toContain(en.cyberscore.checks.edit);
    expect(html).not.toContain(en.cyberscore.checks.delete);
    // Only the category and status filters are selects; the card's outcome and status are chips.
    expect(html.match(/role="combobox"/g)).toHaveLength(2);
  });

  test.each(["ADMIN", "OWNER"] as const)(
    "an %s gets the add control and no read-only note",
    (value) => {
      role = value;
      const html = markup();
      expect(html).toContain(en.cyberscore.checks.add);
      expect(html).toContain(en.cyberscore.checks.edit);
      expect(html).toContain(en.cyberscore.checks.delete);
      expect(html.match(/role="combobox"/g)).toHaveLength(4);
      expect(html).not.toContain(en.cyberscore.checks.readOnly);
    }
  );
});

describe("CheckList cards", () => {
  test("shows the description and the fix steps in full, keeping their line breaks", () => {
    const steps = "1. Renew the licence.\n2. Enforce the policy.\n3. Register a key.";
    const description = "A long description that a reader needs in full.\n\nImpact: everything.";
    checks = [{ ...mfaCheck, description, expected: steps } as Check];
    const html = markup();

    expect(html).toContain(steps);
    expect(html).toContain(description);
    expect(html).toContain("whitespace-pre-line");
    // Nothing on the card cuts its text short.
    const card = html.match(/<article[\s\S]*<\/article>/)?.[0] ?? "";
    expect(card).toContain(steps);
    expect(card).not.toContain("line-clamp");
    expect(card).not.toContain("truncate");
    expect(html).toContain(en.cyberscore.checks.fields.description);
    expect(html).toContain(en.cyberscore.checks.fields.expected);
  });
});

describe("CheckList category groups", () => {
  test("shows one header per category in the declared order, uncategorized last", () => {
    checks = [
      makeCheck("c1", "Backups restore", "DATA"),
      makeCheck("c2", "No category check", null),
      makeCheck("c3", "MFA on admin accounts", "IDENTITY"),
      makeCheck("c4", "Disk encryption", "DATA"),
    ];
    const html = markup();

    const headers = [
      ...html.matchAll(/<h2 id="[^"]*" class="kazan-grid-heading">([^<]*)<\/h2>/g),
    ].map((match) => match[1]);
    expect(headers).toEqual([
      en.cyberscore.categories.IDENTITY,
      en.cyberscore.categories.DATA,
      en.cyberscore.categories.NONE,
    ]);

    // Each check sits under its own header, in the server's order within it.
    const positions = [
      `>${en.cyberscore.categories.IDENTITY}</h2>`,
      "MFA on admin accounts",
      `>${en.cyberscore.categories.DATA}</h2>`,
      "Backups restore",
      "Disk encryption",
      `>${en.cyberscore.categories.NONE}</h2>`,
      "No category check",
    ].map((text) => html.indexOf(text));
    expect(positions.every((position) => position >= 0)).toBe(true);
    expect([...positions].sort((a, b) => a - b)).toEqual(positions);
  });
});

describe("check card saves", () => {
  function SavingCards({ rows, savingCheckIds }: { rows: Check[]; savingCheckIds: Set<string> }) {
    return (
      <>
        {rows.map((check) => (
          <CheckCard
            key={check.id}
            check={check}
            canManage
            isSaving={savingCheckIds.has(check.id)}
            onEdit={() => undefined}
            onDelete={() => undefined}
            onOutcomeChange={() => undefined}
            onStatusChange={() => undefined}
          />
        ))}
      </>
    );
  }

  test("disables the selects of every card whose save is running, and only those", () => {
    const rows = [
      makeCheck("c1", "First check", "IDENTITY"),
      makeCheck("c2", "Second check", "IDENTITY"),
      makeCheck("c3", "Third check", "IDENTITY"),
    ];
    const html = renderToStaticMarkup(
      <NextIntlClientProvider locale="en" messages={en} timeZone="UTC">
        <SavingCards rows={rows} savingCheckIds={new Set(["c1", "c3"])} />
      </NextIntlClientProvider>
    );
    const triggers = html.match(/<button[^>]*role="combobox"[^>]*>/g) ?? [];
    const isDisabled = (label: string) => {
      const trigger = triggers.find((tag) => tag.includes(`aria-label="${label}"`));
      expect(trigger).toBeDefined();
      return / disabled=""/.test(trigger ?? "");
    };
    const { outcome, status } = en.cyberscore.checks.columns;

    expect(isDisabled(`${outcome}: First check`)).toBe(true);
    expect(isDisabled(`${status}: First check`)).toBe(true);
    expect(isDisabled(`${outcome}: Second check`)).toBe(false);
    expect(isDisabled(`${status}: Second check`)).toBe(false);
    expect(isDisabled(`${outcome}: Third check`)).toBe(true);
    expect(isDisabled(`${status}: Third check`)).toBe(true);
  });

  test("keeps a check saving until its own save answers, whatever another row does", async () => {
    let saving: ReadonlySet<string> = new Set();
    const setSaving = (
      update: ReadonlySet<string> | ((current: ReadonlySet<string>) => ReadonlySet<string>)
    ) => {
      saving = typeof update === "function" ? update(saving) : update;
    };
    const first = Promise.withResolvers<void>();
    const second = Promise.withResolvers<void>();
    const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

    trackCheckSave(setSaving, "c1", first.promise);
    trackCheckSave(setSaving, "c2", second.promise);
    expect([...saving]).toEqual(["c1", "c2"]);

    second.resolve();
    await settle();
    expect([...saving]).toEqual(["c1"]);

    // A failed save is toasted by the mutation; the row is released all the same.
    first.reject(new Error("save failed"));
    await settle();
    expect(saving.size).toBe(0);
  });
});

describe("CheckList filters", () => {
  const mixed = () => [
    makeCheck("c1", "MFA on admin accounts", "IDENTITY"),
    makeCheck("c2", "Backups restore", "DATA"),
    makeCheck("c3", "Admin MFA for break-glass", "IDENTITY"),
    makeCheck("c4", "No category check", null),
  ];

  test("filterChecks keeps one category, the uncategorized ones, or every check", () => {
    const names = (list: Check[]) => list.map((check) => check.name);
    expect(names(filterChecks(mixed(), { category: "IDENTITY" }))).toEqual([
      "MFA on admin accounts",
      "Admin MFA for break-glass",
    ]);
    expect(names(filterChecks(mixed(), { category: "NONE" }))).toEqual(["No category check"]);
    expect(filterChecks(mixed(), { category: null, search: null })).toHaveLength(4);
  });

  test("filterChecks matches the name, ignoring case and surrounding spaces", () => {
    const names = (list: Check[]) => list.map((check) => check.name);
    expect(names(filterChecks(mixed(), { search: "  mfa " }))).toEqual([
      "MFA on admin accounts",
      "Admin MFA for break-glass",
    ]);
    expect(names(filterChecks(mixed(), { category: "IDENTITY", search: "break" }))).toEqual([
      "Admin MFA for break-glass",
    ]);
  });

  test("a category in the URL shows only that category's cards", () => {
    checks = mixed();
    filters = { status: null, category: "IDENTITY", search: null, view: "cards" };
    const html = markup();

    expect(html).toContain("MFA on admin accounts");
    expect(html).toContain("Admin MFA for break-glass");
    expect(html).not.toContain("Backups restore");
    expect(html).not.toContain("No category check");
    expect(html.match(/<article/g)).toHaveLength(2);
  });

  test("filters that match nothing say so and offer to clear them", () => {
    checks = mixed();
    filters = { status: null, category: "DATA", search: "mfa", view: "cards" };
    const html = markup();

    expect(html).not.toContain("<article");
    expect(html).toContain(en.cyberscore.checks.emptyFiltered);
    expect(html).toContain(en.cyberscore.checks.filter.clear);
  });
});

describe("CheckList views", () => {
  const mixed = () => [
    makeCheck("c1", "ID-01 MFA on admin accounts", "IDENTITY"),
    { ...makeCheck("c2", "DA-02 Backups restore", "DATA"), status: "IN_PROGRESS" } as Check,
    makeCheck("c3", "No category check", null),
  ];
  const pressed = (html: string) =>
    [...html.matchAll(/aria-label="([^"]*)" aria-pressed="true"/g)].map((match) => match[1]);

  test("the toggle offers cards, list and kanban, and cards is the default", () => {
    checks = mixed();
    const html = markup();
    expect(html).toContain(`aria-label="${en.views.switchToCards}"`);
    expect(html).toContain(`aria-label="${en.views.switchToList}"`);
    expect(html).toContain(`aria-label="${en.views.switchToKanban}"`);
    expect(pressed(html)).toEqual([en.views.switchToCards]);
    expect(html.match(/<article/g)).toHaveLength(3);
  });

  test("the list shows a row per check under a heading row per category", () => {
    checks = mixed();
    filters = { ...filters, view: "list" };
    const html = markup();

    expect(pressed(html)).toEqual([en.views.switchToList]);
    expect(html).not.toContain("<article");
    expect(html).toContain("<table");
    expect(html).toContain(`>ID-01</span>`);
    expect(html).toContain(`>MFA on admin accounts</span>`);
    expect(html).toContain(
      `aria-label="${en.cyberscore.checks.showText}: ID-01 MFA on admin accounts"`
    );
    const positions = [
      en.cyberscore.categories.IDENTITY,
      "MFA on admin accounts",
      en.cyberscore.categories.DATA,
      "Backups restore",
      en.cyberscore.categories.NONE,
      "No category check",
    ].map((text) => html.indexOf(`>${text}<`));
    expect(positions.every((position) => position >= 0)).toBe(true);
    expect([...positions].sort((a, b) => a - b)).toEqual(positions);
  });

  test("the list gives an admin selects and a member chips", () => {
    checks = mixed();
    filters = { ...filters, view: "list" };
    expect(markup().match(/role="combobox"/g)).toHaveLength(2);
    role = "ADMIN";
    expect(markup().match(/role="combobox"/g)).toHaveLength(2 + checks.length * 2);
  });

  test("the board shows a column per status with each check in its own", () => {
    checks = mixed();
    filters = { ...filters, view: "kanban" };
    const html = markup();

    expect(pressed(html)).toEqual([en.views.switchToKanban]);
    const columns = [...html.matchAll(/<section aria-label="([^"]*)"/g)].map((match) => match[1]);
    expect(columns).toEqual([
      en.cyberscore.statuses.NOT_STARTED,
      en.cyberscore.statuses.IN_PROGRESS,
      en.cyberscore.statuses.DONE,
      en.cyberscore.statuses.NOT_APPLICABLE,
    ]);
    const column = (status: string) =>
      html.split(`<section aria-label="${status}"`)[1]?.split("</section>")[0] ?? "";
    expect(column(en.cyberscore.statuses.IN_PROGRESS)).toContain("DA-02 Backups restore");
    expect(column(en.cyberscore.statuses.DONE)).toContain("ID-01 MFA on admin accounts");
    expect(column(en.cyberscore.statuses.DONE)).toContain("No category check");
    expect(column(en.cyberscore.statuses.NOT_STARTED)).toContain(
      en.cyberscore.checks.kanban.emptyColumn
    );
  });

  test("only an admin's board cards can be picked up", () => {
    checks = mixed();
    filters = { ...filters, view: "kanban" };
    expect(markup()).not.toContain('aria-roledescription="sortable"');
    role = "OWNER";
    expect(markup().match(/aria-roledescription="sortable"/g)).toHaveLength(3);
  });

  test("the board goes full width; cards and list keep the compact width", () => {
    checks = mixed();
    expect(markup()).toContain("max-w-6xl");
    filters = { ...filters, view: "list" };
    expect(markup()).toContain("max-w-6xl");
    filters = { ...filters, view: "kanban" };
    expect(markup()).not.toContain("max-w-6xl");
  });
});
