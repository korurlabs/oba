import { afterAll, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import type { ColumnDef } from "@tanstack/react-table";
import type { MemberAppAccess } from "@oba/shared/features/organizations/schemas";

const actualIntl = { ...(await import("next-intl")) };

mock.module("next-intl", () => ({
  ...actualIntl,
  useTranslations: () => (key: string) => key,
}));

afterAll(() => {
  mock.module("next-intl", () => actualIntl);
});

const { useAppAccessColumns } = await import("./use-app-access-columns");

function member(overrides: Partial<MemberAppAccess>): MemberAppAccess {
  return {
    userId: "u-1",
    fullName: "Ada Lovelace",
    role: "MEMBER",
    apps: [],
    ...overrides,
  } as MemberAppAccess;
}

function renderCell(columnId: string, row: MemberAppAccess): string {
  let columns: ColumnDef<MemberAppAccess, unknown>[] | undefined;
  function Probe() {
    columns = useAppAccessColumns();
    return null;
  }
  renderToStaticMarkup(<Probe />);
  const Cell = columns!.find((column) => column.id === columnId)!.cell as (context: {
    row: { original: MemberAppAccess };
  }) => ReactNode;
  return renderToStaticMarkup(<>{Cell({ row: { original: row } })}</>);
}

describe("useAppAccessColumns", () => {
  test("shows the member's full name", () => {
    expect(renderCell("fullName", member({ fullName: "Grace Hopper" }))).toContain("Grace Hopper");
  });

  test("shows one badge per app with the app name", () => {
    const html = renderCell("apps", member({ apps: ["KAIZEN", "KAYA"] }));

    expect(html).toContain("kaizen.name");
    expect(html).toContain("kaya.name");
  });

  test("shows an app value this build does not know as it came", () => {
    const html = renderCell("apps", member({ apps: ["LATER_APP"] }));

    expect(html).toContain("LATER_APP");
  });

  test("names the Kalkan and Otak grants", () => {
    const html = renderCell("apps", member({ apps: ["KALKAN", "OTAK"] }));

    expect(html).toContain("kalkan.name");
    expect(html).toContain("otak.name");
  });

  test("shows the no-apps label for a member without apps", () => {
    expect(renderCell("apps", member({ apps: [] }))).toContain("access.noApps");
  });
});
