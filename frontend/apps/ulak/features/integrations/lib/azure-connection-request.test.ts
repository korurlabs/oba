import { describe, expect, test } from "bun:test";
import type { AzureConnection } from "../schemas";
import { toAzureConnectionFormValues, toAzureConnectionRequest } from "./azure-connection-request";

const TENANT = "11111111-1111-4111-8111-111111111111";
const CLIENT = "22222222-2222-4222-8222-222222222222";

describe("toAzureConnectionRequest", () => {
  test("sends trimmed values", () => {
    expect(
      toAzureConnectionRequest({
        name: "  Production  ",
        tenantId: ` ${TENANT} `,
        clientId: ` ${CLIENT} `,
      })
    ).toEqual({ name: "Production", tenantId: TENANT, clientId: CLIENT });
  });

  test("sends an empty client id as null, which clears it on update", () => {
    expect(
      toAzureConnectionRequest({ name: "Production", tenantId: TENANT, clientId: "  " })
    ).toEqual({
      name: "Production",
      tenantId: TENANT,
      clientId: null,
    });
  });
});

describe("toAzureConnectionFormValues", () => {
  test("is an empty form without a connection", () => {
    expect(toAzureConnectionFormValues(undefined)).toEqual({
      name: "",
      tenantId: "",
      clientId: "",
    });
  });

  test("holds a missing client id as an empty field", () => {
    const connection = { name: "Production", tenantId: TENANT, clientId: null } as AzureConnection;
    expect(toAzureConnectionFormValues(connection)).toEqual({
      name: "Production",
      tenantId: TENANT,
      clientId: "",
    });
  });
});
