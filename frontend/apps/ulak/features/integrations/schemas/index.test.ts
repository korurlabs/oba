import { describe, expect, test } from "bun:test";
import { AZURE_CONNECTION_LIMITS, azureConnectionFormSchema, azureConnectionSchema } from "./index";

const schema = azureConnectionFormSchema((key) => key);

const TENANT = "11111111-1111-4111-8111-111111111111";
const CLIENT = "22222222-2222-4222-8222-222222222222";
const valid = { name: "Production tenant", tenantId: TENANT, clientId: "" };

function messageOf(values: Record<string, string>, field: string): string | undefined {
  const result = schema.safeParse(values);
  return result.success
    ? undefined
    : result.error.issues.find((issue) => issue.path[0] === field)?.message;
}

describe("azureConnectionFormSchema", () => {
  test("accepts a name and a tenant id without a client id", () => {
    expect(schema.safeParse(valid).success).toBe(true);
  });

  test("accepts a client id that is a UUID", () => {
    expect(schema.safeParse({ ...valid, clientId: CLIENT }).success).toBe(true);
  });

  test("refuses a blank name", () => {
    expect(messageOf({ ...valid, name: "   " }, "name")).toBe("form.nameRequired");
  });

  test("refuses a name longer than the backend allows", () => {
    const max = AZURE_CONNECTION_LIMITS.name;
    expect(messageOf({ ...valid, name: "x".repeat(max) }, "name")).toBeUndefined();
    expect(messageOf({ ...valid, name: "x".repeat(max + 1) }, "name")).toBe("form.nameMaxLength");
  });

  test("checks the length of the trimmed name, which is what is sent", () => {
    const max = AZURE_CONNECTION_LIMITS.name;
    expect(messageOf({ ...valid, name: ` ${"x".repeat(max)} ` }, "name")).toBeUndefined();
  });

  test("refuses a missing tenant id with its own message", () => {
    expect(messageOf({ ...valid, tenantId: "" }, "tenantId")).toBe("form.tenantIdRequired");
  });

  test.each(["contoso.onmicrosoft.com", "1234", `${TENANT}x`])(
    "refuses the tenant id %s, which is no UUID",
    (tenantId) => {
      expect(messageOf({ ...valid, tenantId }, "tenantId")).toBe("form.tenantIdFormat");
    }
  );

  test("refuses a client id that is no UUID", () => {
    expect(messageOf({ ...valid, clientId: "my-app" }, "clientId")).toBe("form.clientIdFormat");
  });

  test("accepts ids with surrounding spaces, because they are sent trimmed", () => {
    expect(
      schema.safeParse({ ...valid, tenantId: ` ${TENANT} `, clientId: ` ${CLIENT} ` }).success
    ).toBe(true);
  });
});

describe("azureConnectionSchema", () => {
  const connection = {
    id: "33333333-3333-4333-8333-333333333333",
    name: "Production tenant",
    tenantId: TENANT,
    clientId: null,
    status: "PENDING",
    lastVerifiedAt: null,
    lastError: null,
    createdAt: "2026-11-28T09:00:00Z",
    subscriptions: [],
    setup: {
      issuer: null,
      subject: `oba:organization:${TENANT}:connection:33333333-3333-4333-8333-333333333333`,
      audience: "api://AzureADTokenExchange",
    },
  };

  test("parses a new connection without client id, results or issuer", () => {
    expect(azureConnectionSchema.safeParse(connection).success).toBe(true);
  });

  test("parses a checked connection with its subscriptions", () => {
    const checked = {
      ...connection,
      clientId: CLIENT,
      status: "FAILING",
      lastVerifiedAt: "2026-11-28T04:30:00Z",
      lastError: "AZR_UPS_009:AADSTS700213",
      subscriptions: [{ subscriptionId: CLIENT, displayName: "Production", state: "Enabled" }],
      setup: { ...connection.setup, issuer: "https://oba.example.test" },
    };
    expect(azureConnectionSchema.safeParse(checked).success).toBe(true);
  });

  test("refuses a status the client does not know", () => {
    expect(azureConnectionSchema.safeParse({ ...connection, status: "DISABLED" }).success).toBe(
      false
    );
  });
});
