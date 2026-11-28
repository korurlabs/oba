import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import en from "@/locales/en.json";
import type { AzureConnection } from "../schemas";

// The messages, the snippets and the date display are real. The sheet frame
// is a plain container, because Radix renders an open sheet into a portal that
// a static render does not print, and the verify hook is a fixed value.
let verifying: string | undefined;

const actualSheet = { ...(await import("@oba/ui/sky/sheet")) };
const actualHooks = { ...(await import("../hooks/use-azure-connections")) };

function Frame({ children }: { children?: ReactNode }) {
  return <div>{children}</div>;
}

mock.module("@oba/ui/sky/sheet", () => ({
  ...actualSheet,
  Sheet: ({ open, children }: { open: boolean; children?: ReactNode }) =>
    open ? <div data-sheet="">{children}</div> : null,
  SheetContent: Frame,
  SheetHeader: Frame,
  SheetTitle: ({ children }: { children?: ReactNode }) => <h2>{children}</h2>,
  SheetDescription: ({ children }: { children?: ReactNode }) => <p>{children}</p>,
}));
mock.module("../hooks/use-azure-connections", () => ({
  ...actualHooks,
  useVerifyAzureConnection: () => ({
    isPending: verifying !== undefined,
    variables: verifying,
    mutate: () => undefined,
  }),
}));

afterAll(() => {
  mock.module("@oba/ui/sky/sheet", () => actualSheet);
  mock.module("../hooks/use-azure-connections", () => actualHooks);
});

const { AzureConnectionSheet } = await import("./azure-connection-sheet");

const ID = "c0a80101-0000-4000-8000-000000000001";
const SUBJECT = `oba:organization:org-1:connection:${ID}`;
const texts = en.integrations.azure;

function connection(overrides: Partial<AzureConnection> = {}): AzureConnection {
  return {
    id: ID,
    name: "Production tenant",
    tenantId: "8f0e2c1a-4b5d-4e6f-9a7b-1c2d3e4f5a6b",
    clientId: "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
    status: "CONNECTED",
    lastVerifiedAt: "2026-11-27T04:30:00Z",
    lastError: null,
    createdAt: "2026-11-20T09:00:00Z",
    subscriptions: [
      {
        subscriptionId: "0b1c2d3e-4f5a-4b6c-8d7e-9f0a1b2c3d4e",
        displayName: "Production",
        state: "Enabled",
      },
    ],
    setup: {
      issuer: "https://api.example.com",
      subject: SUBJECT,
      audience: "api://AzureADTokenExchange",
    },
    ...overrides,
  };
}

function render(target: AzureConnection | null, open = true): string {
  return renderToStaticMarkup(
    <NextIntlClientProvider locale="en" messages={en} timeZone="UTC">
      <AzureConnectionSheet
        organizationId="org-1"
        connection={target}
        open={open}
        onOpenChange={() => undefined}
      />
    </NextIntlClientProvider>
  );
}

beforeEach(() => {
  verifying = undefined;
});

describe("AzureConnectionSheet", () => {
  test("shows the status as text, the last check and no error", () => {
    const html = render(connection());

    expect(html).toContain("<h2>Production tenant</h2>");
    expect(html).toContain(`<dd>${texts.statuses.CONNECTED}</dd>`);
    expect(html).toContain("2026");
    expect(html).toContain(texts.noError);
  });

  test("a connection never checked says so", () => {
    const html = render(connection({ status: "PENDING", lastVerifiedAt: null, subscriptions: [] }));

    expect(html).toContain(`<dd>${texts.statuses.PENDING}</dd>`);
    expect(html).toContain(`<dd>${texts.neverVerified}</dd>`);
    expect(html).toContain(texts.subscriptions.empty);
  });

  test("a failed check explains its code and names Entra's code", () => {
    const html = render(connection({ status: "FAILING", lastError: "AZR_UPS_009:AADSTS700213" }));

    expect(html).toContain(texts.lastErrors.AZR_UPS_009);
    expect(html).toContain("Entra code AADSTS700213");
  });

  test("an unknown error code is named as it is", () => {
    expect(render(connection({ lastError: "OID_NOT_001" }))).toContain(
      "The check failed with error OID_NOT_001."
    );
  });

  test("shows the three setup values to copy", () => {
    const html = render(connection());

    expect(html).toContain(texts.setup.issuerLabel);
    expect(html).toContain("https://api.example.com");
    expect(html).toContain(SUBJECT);
    expect(html).toContain("api://AzureADTokenExchange");
  });

  test("offers the managed identity first and the app registration second, az CLI first", () => {
    const html = render(connection());

    const managed = html.indexOf(texts.setup.managedIdentityTab);
    const app = html.indexOf(texts.setup.appRegistrationTab);
    expect(managed).toBeGreaterThan(-1);
    expect(app).toBeGreaterThan(managed);
    expect(html).toContain(texts.setup.azCliTab);
    expect(html).toContain(texts.setup.terraformTab);
    // The open tab is the managed identity's az CLI snippet, filled with the subject.
    expect(html).toContain("az identity federated-credential create");
    expect(html).toContain(texts.setup.placeholdersNote);
  });

  test("without an issuer there is nothing to set up", () => {
    const html = render(connection({ setup: { ...connection().setup, issuer: null } }));

    expect(html).toContain(texts.setup.noIssuer);
    expect(html).not.toContain(texts.setup.managedIdentityTab);
    expect(html).not.toContain(SUBJECT);
  });

  test("lists the subscriptions found", () => {
    const html = render(connection());

    expect(html).toContain("Production");
    expect(html).toContain("0b1c2d3e-4f5a-4b6c-8d7e-9f0a1b2c3d4e");
    expect(html).toContain("Enabled");
  });

  test("offers Verify, and shows the running check of this connection", () => {
    expect(render(connection())).toContain(texts.actions.verify);

    verifying = ID;
    const html = render(connection());
    expect(html).toContain(texts.actions.verifying);
    expect(html).toContain('aria-busy="true"');
  });

  test("renders nothing while closed or without a connection", () => {
    expect(render(connection(), false)).toBe("");
    expect(render(null)).toBe("");
  });
});
