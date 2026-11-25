import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";
import { NextIntlClientProvider } from "next-intl";
import en from "@/locales/en.json";

// The user is a stand-in; the gate, the empty state and the messages are real.
let meRole: string | undefined;

const actualMe = { ...(await import("@oba/shared/features/users/hooks/use-get-me")) };

mock.module("@oba/shared/features/users/hooks/use-get-me", () => ({
  ...actualMe,
  useGetMe: () => ({
    data: {
      data: {
        id: "me-1",
        defaultOrganizationId: "org-1",
        organizations: meRole ? [{ id: "org-1", name: "Acme", role: meRole }] : [],
      },
    },
  }),
}));

afterAll(() => {
  mock.module("@oba/shared/features/users/hooks/use-get-me", () => actualMe);
});

const { CyberscoreAccessGate } = await import("./cyberscore-access-gate");

function markup(): string {
  return renderToStaticMarkup(
    <NextIntlClientProvider locale="en" messages={en} timeZone="UTC">
      <CyberscoreAccessGate>
        <p data-marker="page" />
      </CyberscoreAccessGate>
    </NextIntlClientProvider>
  );
}

beforeEach(() => {
  meRole = undefined;
});

describe("CyberscoreAccessGate", () => {
  for (const role of ["OWNER", "ADMIN", "MEMBER"]) {
    test(`renders the page for a ${role}`, () => {
      meRole = role;
      const html = markup();
      expect(html).toContain('data-marker="page"');
      expect(html).not.toContain(en.cyberscore.noAccess.title);
    });
  }

  test("shows the no-access state instead of the page for a GUEST", () => {
    meRole = "GUEST";
    const html = markup();
    expect(html).not.toContain('data-marker="page"');
    expect(html).toContain(en.cyberscore.noAccess.title);
    expect(html).toContain("You are a guest in Acme");
  });

  test("renders the page while the role is not known, so the API decides", () => {
    expect(markup()).toContain('data-marker="page"');
  });
});
