import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";
import { NextIntlClientProvider } from "next-intl";
import en from "@/locales/en.json";
import { SenseyTable } from "@oba/shared/components/sensey-table";
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
  useQueryStates: () => [{ status: null }, () => Promise.resolve(new URLSearchParams())],
}));

afterAll(() => {
  mock.module("@oba/shared/features/users/hooks/use-get-me", () => actualGetMe);
  mock.module("../hooks/use-checks", () => actualChecks);
  mock.module("@oba/shared/libs/i18n/navigation", () => actualNavigation);
  mock.module("nuqs", () => actualNuqs);
});

const { CheckList, trackCheckSave } = await import("./check-list");
const { useCheckColumns } = await import("../hooks/use-check-columns");

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
    // Only the status filter is a select; the row's outcome and status are chips.
    expect(html.match(/role="combobox"/g)).toHaveLength(1);
  });

  test.each(["ADMIN", "OWNER"] as const)(
    "an %s gets the add control and no read-only note",
    (value) => {
      role = value;
      const html = markup();
      expect(html).toContain(en.cyberscore.checks.add);
      expect(html).toContain(en.cyberscore.checks.edit);
      expect(html).toContain(en.cyberscore.checks.delete);
      expect(html.match(/role="combobox"/g)).toHaveLength(3);
      expect(html).not.toContain(en.cyberscore.checks.readOnly);
    }
  );
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

    const headers = [...html.matchAll(/<span class="kazan-grid-heading">([^<]*)<\/span>/g)].map(
      (match) => match[1]
    );
    expect(headers).toEqual([
      en.cyberscore.categories.IDENTITY,
      en.cyberscore.categories.DATA,
      en.cyberscore.categories.NONE,
    ]);

    // Each check sits under its own header, in the server's order within it.
    const positions = [
      `>${en.cyberscore.categories.IDENTITY}</span>`,
      "MFA on admin accounts",
      `>${en.cyberscore.categories.DATA}</span>`,
      "Backups restore",
      "Disk encryption",
      `>${en.cyberscore.categories.NONE}</span>`,
      "No category check",
    ].map((text) => html.indexOf(text));
    expect(positions.every((position) => position >= 0)).toBe(true);
    expect([...positions].sort((a, b) => a - b)).toEqual(positions);
  });
});

describe("check row saves", () => {
  function SavingTable({ rows, savingCheckIds }: { rows: Check[]; savingCheckIds: Set<string> }) {
    const columns = useCheckColumns({
      canManage: true,
      onEdit: () => undefined,
      onDelete: () => undefined,
      onOutcomeChange: () => undefined,
      onStatusChange: () => undefined,
      savingCheckIds,
    });
    return <SenseyTable<Check> columns={columns} data={rows} getRowId={(check) => check.id} />;
  }

  test("disables the selects of every row whose save is running, and only those", () => {
    const rows = [
      makeCheck("c1", "First check", "IDENTITY"),
      makeCheck("c2", "Second check", "IDENTITY"),
      makeCheck("c3", "Third check", "IDENTITY"),
    ];
    const html = renderToStaticMarkup(
      <NextIntlClientProvider locale="en" messages={en} timeZone="UTC">
        <SavingTable rows={rows} savingCheckIds={new Set(["c1", "c3"])} />
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
