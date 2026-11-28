import type { AzureConnection } from "../schemas";

/**
 * Setup snippets an admin runs in their own Azure tenant to make Oba's
 * assertions trusted: an identity, a federated credential with the
 * connection's issuer, subject and audience, and Reader on one subscription.
 *
 * The connection's own values are filled in; what only the admin knows
 * (subscription, resource group, location) stays a placeholder they replace.
 * The identity and credential names are generic so the snippets carry no
 * organization data beyond what the connection already shows.
 */

/** Placeholders the admin replaces before running a snippet. */
export const SETUP_PLACEHOLDERS = {
  subscriptionId: "<subscription-id>",
  resourceGroup: "<resource-group>",
  location: "<location>",
} as const;

/** The name of the identity or app registration the snippets create. */
export const SETUP_IDENTITY_NAME = "oba-reader";

/** The name of the federated credential the snippets create on it. */
export const SETUP_CREDENTIAL_NAME = "oba";

export interface AzureSetupValues {
  issuer: string;
  subject: string;
  audience: string;
  tenantId: string;
}

export interface AzureSetupSnippet {
  azCli: string;
  terraform: string;
}

export interface AzureSetupSnippets {
  /** User-assigned managed identity, the recommended way: shown first. */
  managedIdentity: AzureSetupSnippet;
  /** App registration with a service principal, for tenants that prefer one. */
  appRegistration: AzureSetupSnippet;
}

/**
 * The values the snippets need, or null while the deployment has no issuer:
 * Azure cannot trust Oba then, so there is nothing to set up.
 */
export function toAzureSetupValues(connection: AzureConnection): AzureSetupValues | null {
  if (!connection.setup.issuer) return null;
  return {
    issuer: connection.setup.issuer,
    subject: connection.setup.subject,
    audience: connection.setup.audience,
    tenantId: connection.tenantId,
  };
}

export function buildAzureSetupSnippets(values: AzureSetupValues): AzureSetupSnippets {
  return {
    managedIdentity: {
      azCli: managedIdentityAzCli(values),
      terraform: managedIdentityTerraform(values),
    },
    appRegistration: {
      azCli: appRegistrationAzCli(values),
      terraform: appRegistrationTerraform(values),
    },
  };
}

const { subscriptionId, resourceGroup, location } = SETUP_PLACEHOLDERS;
const identity = SETUP_IDENTITY_NAME;
const credential = SETUP_CREDENTIAL_NAME;
const readerScope = `/subscriptions/${subscriptionId}`;

function managedIdentityAzCli({ issuer, subject, audience, tenantId }: AzureSetupValues): string {
  return [
    `az login --tenant "${tenantId}"`,
    `az account set --subscription "${subscriptionId}"`,
    "",
    `az identity create --name "${identity}" --resource-group "${resourceGroup}"`,
    "",
    `az identity federated-credential create \\`,
    `  --name "${credential}" \\`,
    `  --identity-name "${identity}" \\`,
    `  --resource-group "${resourceGroup}" \\`,
    `  --issuer "${issuer}" \\`,
    `  --subject "${subject}" \\`,
    `  --audiences "${audience}"`,
    "",
    `PRINCIPAL_ID="$(az identity show --name "${identity}" --resource-group "${resourceGroup}" --query principalId --output tsv)"`,
    `az role assignment create \\`,
    `  --assignee-object-id "$PRINCIPAL_ID" \\`,
    `  --assignee-principal-type ServicePrincipal \\`,
    `  --role Reader \\`,
    `  --scope "${readerScope}"`,
    "",
    "# The client id to enter on this connection:",
    `az identity show --name "${identity}" --resource-group "${resourceGroup}" --query clientId --output tsv`,
    "",
  ].join("\n");
}

function managedIdentityTerraform({
  issuer,
  subject,
  audience,
  tenantId,
}: AzureSetupValues): string {
  return [
    `provider "azurerm" {`,
    `  features {}`,
    `  tenant_id       = "${tenantId}"`,
    `  subscription_id = "${subscriptionId}"`,
    `}`,
    "",
    `resource "azurerm_user_assigned_identity" "oba" {`,
    `  name                = "${identity}"`,
    `  resource_group_name = "${resourceGroup}"`,
    `  location            = "${location}"`,
    `}`,
    "",
    `resource "azurerm_federated_identity_credential" "oba" {`,
    `  name                = "${credential}"`,
    `  resource_group_name = azurerm_user_assigned_identity.oba.resource_group_name`,
    `  parent_id           = azurerm_user_assigned_identity.oba.id`,
    `  issuer              = "${issuer}"`,
    `  subject             = "${subject}"`,
    `  audience            = ["${audience}"]`,
    `}`,
    "",
    `resource "azurerm_role_assignment" "oba_reader" {`,
    `  scope                = "${readerScope}"`,
    `  role_definition_name = "Reader"`,
    `  principal_id         = azurerm_user_assigned_identity.oba.principal_id`,
    `}`,
    "",
    "# The client id to enter on this connection.",
    `output "oba_client_id" {`,
    `  value = azurerm_user_assigned_identity.oba.client_id`,
    `}`,
    "",
  ].join("\n");
}

function appRegistrationAzCli({ issuer, subject, audience, tenantId }: AzureSetupValues): string {
  const parameters = JSON.stringify({
    name: credential,
    issuer,
    subject,
    audiences: [audience],
  });
  return [
    `az login --tenant "${tenantId}"`,
    "",
    `APP_ID="$(az ad app create --display-name "${identity}" --query appId --output tsv)"`,
    `az ad sp create --id "$APP_ID"`,
    "",
    `az ad app federated-credential create \\`,
    `  --id "$APP_ID" \\`,
    `  --parameters '${parameters}'`,
    "",
    `az role assignment create \\`,
    `  --assignee "$APP_ID" \\`,
    `  --role Reader \\`,
    `  --scope "${readerScope}"`,
    "",
    "# The client id to enter on this connection:",
    `echo "$APP_ID"`,
    "",
  ].join("\n");
}

function appRegistrationTerraform({
  issuer,
  subject,
  audience,
  tenantId,
}: AzureSetupValues): string {
  return [
    `provider "azuread" {`,
    `  tenant_id = "${tenantId}"`,
    `}`,
    "",
    `provider "azurerm" {`,
    `  features {}`,
    `  tenant_id       = "${tenantId}"`,
    `  subscription_id = "${subscriptionId}"`,
    `}`,
    "",
    `resource "azuread_application" "oba" {`,
    `  display_name = "${identity}"`,
    `}`,
    "",
    `resource "azuread_application_federated_identity_credential" "oba" {`,
    `  application_id = azuread_application.oba.id`,
    `  display_name   = "${credential}"`,
    `  issuer         = "${issuer}"`,
    `  subject        = "${subject}"`,
    `  audiences      = ["${audience}"]`,
    `}`,
    "",
    `resource "azuread_service_principal" "oba" {`,
    `  client_id = azuread_application.oba.client_id`,
    `}`,
    "",
    `resource "azurerm_role_assignment" "oba_reader" {`,
    `  scope                = "${readerScope}"`,
    `  role_definition_name = "Reader"`,
    `  principal_id         = azuread_service_principal.oba.object_id`,
    `}`,
    "",
    "# The client id to enter on this connection.",
    `output "oba_client_id" {`,
    `  value = azuread_application.oba.client_id`,
    `}`,
    "",
  ].join("\n");
}
