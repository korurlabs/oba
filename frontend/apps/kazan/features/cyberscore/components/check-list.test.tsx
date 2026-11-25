import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";
import { NextIntlClientProvider } from "next-intl";
import en from "@/locales/en.json";
import type { Check } from "../schemas";

// Who the reader is and which checks come back are stand-ins; the list, its
// columns, the access rule and the messages are real.
let role: "OWNER" | "ADMIN" | "MEMBER";

const ORGANIZATION_ID = "org-1";
const checks: Check[] = [
  {
    id: "c1",
    name: "MFA on admin accounts",
    description: null,
    expected: "Every admin uses MFA",
    weight: 20,
    sortOrder: 0,
    category: "IDENTITY",
    outcome: "FAIL",
    status: "DONE",
    updatedAt: new Date("2026-11-25T08:30:00Z"),
  } as unknown as Check,
];

const actualGetMe = { ...(await import("@oba/shared/features/users/hooks/use-get-me")) };
const actualChecks = { ...(await import("../hooks/use-checks")) };
const actualNavigation = { ...(await import("@oba/shared/libs/i18n/navigation")) };
const actualNuqs = { ...(await import("nuqs")) };

const idleMutation = { isPending: false, variables: undefined, mutate: () => undefined };

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

const { CheckList } = await import("./check-list");

function markup(): string {
  return renderToStaticMarkup(
    <NextIntlClientProvider locale="en" messages={en} timeZone="UTC">
      <CheckList />
    </NextIntlClientProvider>
  );
}

beforeEach(() => {
  role = "MEMBER";
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
