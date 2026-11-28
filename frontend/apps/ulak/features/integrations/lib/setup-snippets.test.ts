import { describe, expect, test } from "bun:test";
import type { AzureConnection } from "../schemas";
import {
  SETUP_PLACEHOLDERS,
  buildAzureSetupSnippets,
  toAzureSetupValues,
  type AzureSetupValues,
} from "./setup-snippets";

const values: AzureSetupValues = {
  issuer: "https://oba.example.test",
  subject:
    "oba:organization:44444444-4444-4444-8444-444444444444:connection:55555555-5555-4555-8555-555555555555",
  audience: "api://AzureADTokenExchange",
  tenantId: "11111111-1111-4111-8111-111111111111",
};

const snippets = buildAzureSetupSnippets(values);
const all = [
  ["managed identity, az CLI", snippets.managedIdentity.azCli],
  ["managed identity, Terraform", snippets.managedIdentity.terraform],
  ["app registration, az CLI", snippets.appRegistration.azCli],
  ["app registration, Terraform", snippets.appRegistration.terraform],
] as const;

describe("buildAzureSetupSnippets", () => {
  test.each(all)("%s is filled with the issuer, subject, audience and tenant id", (_, snippet) => {
    expect(snippet).toContain(values.issuer);
    expect(snippet).toContain(values.subject);
    expect(snippet).toContain(values.audience);
    expect(snippet).toContain(values.tenantId);
  });

  test.each(all)(
    "%s keeps the subscription as a placeholder and grants Reader on it",
    (_, snippet) => {
      expect(snippet).toContain(`/subscriptions/${SETUP_PLACEHOLDERS.subscriptionId}`);
      expect(snippet).toContain("Reader");
    }
  );

  test("the managed identity CLI creates the identity, its credential and the role", () => {
    const cli = snippets.managedIdentity.azCli;
    expect(cli).toContain(
      `az identity create --name "oba-reader" --resource-group "<resource-group>"`
    );
    expect(cli).toContain("az identity federated-credential create");
    expect(cli).toContain(`--issuer "${values.issuer}"`);
    expect(cli).toContain(`--subject "${values.subject}"`);
    expect(cli).toContain(`--audiences "${values.audience}"`);
    expect(cli).toContain("az role assignment create");
    expect(cli).toContain("--query clientId");
  });

  test("the managed identity Terraform declares the three resources", () => {
    const tf = snippets.managedIdentity.terraform;
    expect(tf).toContain(`resource "azurerm_user_assigned_identity" "oba"`);
    expect(tf).toContain(`resource "azurerm_federated_identity_credential" "oba"`);
    expect(tf).toContain(`resource "azurerm_role_assignment" "oba_reader"`);
    expect(tf).toContain(`location            = "${SETUP_PLACEHOLDERS.location}"`);
    expect(tf).toContain(`audience            = ["${values.audience}"]`);
  });

  test("the app registration CLI sends the credential as valid JSON", () => {
    const cli = snippets.appRegistration.azCli;
    expect(cli).toContain("az ad app create");
    expect(cli).toContain("az ad app federated-credential create");
    const json = /--parameters '([^']+)'/.exec(cli)?.[1];
    expect(JSON.parse(json ?? "null")).toEqual({
      name: "oba",
      issuer: values.issuer,
      subject: values.subject,
      audiences: [values.audience],
    });
  });

  test("the app registration Terraform declares the application, credential and principal", () => {
    const tf = snippets.appRegistration.terraform;
    expect(tf).toContain(`resource "azuread_application" "oba"`);
    expect(tf).toContain(`resource "azuread_application_federated_identity_credential" "oba"`);
    expect(tf).toContain(`resource "azuread_service_principal" "oba"`);
    expect(tf).toContain(`audiences      = ["${values.audience}"]`);
  });
});

describe("toAzureSetupValues", () => {
  const connection = {
    tenantId: values.tenantId,
    setup: { issuer: values.issuer, subject: values.subject, audience: values.audience },
  } as AzureConnection;

  test("takes the setup values and the tenant id of the connection", () => {
    expect(toAzureSetupValues(connection)).toEqual(values);
  });

  test("is null while the deployment has no issuer", () => {
    expect(
      toAzureSetupValues({ ...connection, setup: { ...connection.setup, issuer: null } })
    ).toBeNull();
  });
});
