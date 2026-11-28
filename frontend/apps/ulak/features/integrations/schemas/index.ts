import { z } from "zod";
import { instantSchema } from "@oba/shared/lib/date-schemas";

/**
 * Azure connections: each one lets Oba read one Azure tenant through a
 * federated credential that trusts Oba as an OpenID Connect issuer, so no
 * secret is ever stored. They are read and written only by an OWNER or ADMIN,
 * through `/azure/connections` with the organization in `X-Organization-ID`.
 */

// ── Enum Schemas ────────────────────────────────────────────────────

/**
 * PENDING until a check first reads a subscription, CONNECTED while checks
 * succeed, FAILING once a check fails after the connection was CONNECTED.
 */
export const AzureConnectionStatusSchema = z.enum(["PENDING", "CONNECTED", "FAILING"]);
export type AzureConnectionStatus = z.infer<typeof AzureConnectionStatusSchema>;
export const AzureConnectionStatus = AzureConnectionStatusSchema.enum;

// ── Field limits ───────────────────────────────────────────────────

/** The backend's bean validation limits, so the form refuses what the API would. */
export const AZURE_CONNECTION_LIMITS = {
  name: 255,
} as const;

// ── Request Schemas ─────────────────────────────────────────────────

/**
 * Body of both POST /azure/connections and PUT /azure/connections/{connectionId}.
 * The update is a full replace: a null client id clears it. A changed tenant
 * or client id sets the connection back to PENDING and clears its results.
 */
export const azureConnectionRequestSchema = z.object({
  name: z.string(),
  tenantId: z.string(),
  clientId: z.string().nullable(),
});

// ── Form Schemas ────────────────────────────────────────────────────

const uuidSchema = z.uuid();

function isUuid(value: string): boolean {
  return uuidSchema.safeParse(value).success;
}

/**
 * Create and edit form of an Azure connection. The client id is optional,
 * because the identity it names is often created only after the connection
 * shows its subject; an empty one is held as "" here and sent as null by
 * toAzureConnectionRequest. Values are checked trimmed, because they are
 * sent trimmed.
 */
export const azureConnectionFormSchema = (t: (key: string) => string) =>
  z.object({
    name: z
      .string()
      .trim()
      .min(1, t("form.nameRequired"))
      .max(AZURE_CONNECTION_LIMITS.name, t("form.nameMaxLength")),
    tenantId: z
      .string()
      .trim()
      .min(1, t("form.tenantIdRequired"))
      .refine((value) => value === "" || isUuid(value), t("form.tenantIdFormat")),
    clientId: z
      .string()
      .trim()
      .refine((value) => value === "" || isUuid(value), t("form.clientIdFormat")),
  });

// ── Response Schemas ────────────────────────────────────────────────

/** A subscription the connection's identity could read at the last successful check. */
export const azureConnectionSubscriptionSchema = z.object({
  subscriptionId: z.guid(),
  displayName: z.string(),
  state: z.string(),
});

/**
 * The three values the admin enters on the federated credential in Azure.
 * The issuer is null when this deployment has no issuer configured; the
 * subject is fixed when the connection is created and never changes.
 */
export const azureConnectionSetupSchema = z.object({
  issuer: z.string().nullable(),
  subject: z.string(),
  audience: z.string(),
});

/**
 * An Azure connection as GET /azure/connections lists it, in name order, and
 * as every write answers it. `lastError` is an Oba error code, followed by
 * ":" and the Entra AADSTS code when Entra sent one; it never carries an
 * Azure message. The subscriptions are those of the last successful check.
 */
export const azureConnectionSchema = z.object({
  id: z.guid(),
  name: z.string(),
  tenantId: z.guid(),
  clientId: z.guid().nullable(),
  status: AzureConnectionStatusSchema,
  lastVerifiedAt: instantSchema.nullable(),
  lastError: z.string().nullable(),
  createdAt: instantSchema,
  subscriptions: z.array(azureConnectionSubscriptionSchema),
  setup: azureConnectionSetupSchema,
});

// ── Inferred Types ──────────────────────────────────────────────────

export type AzureConnectionRequest = z.infer<typeof azureConnectionRequestSchema>;
export type AzureConnectionFormValues = z.infer<ReturnType<typeof azureConnectionFormSchema>>;
export type AzureConnectionSubscription = z.infer<typeof azureConnectionSubscriptionSchema>;
export type AzureConnectionSetup = z.infer<typeof azureConnectionSetupSchema>;
export type AzureConnection = z.infer<typeof azureConnectionSchema>;
