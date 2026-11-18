import { z } from "zod";

import { machineUsageResponseSchema } from "@/features/machines/schemas";

import { AccountProviderSchema } from "./provider";

// ── Enum Schemas ───────────────────────────────────────────────────
export {
  AccountProvider,
  AccountProviderSchema,
  PROVIDER_LABELS,
  providerReportsUsage,
} from "./provider";

/** How a user came to be linked to the account: seen by the recorder, or added by hand. */
export const AccountUserSourceSchema = z.enum(["RECORDER", "MANUAL"]);
export type AccountUserSource = z.infer<typeof AccountUserSourceSchema>;
export const AccountUserSource = AccountUserSourceSchema.enum;

// ── Request Schemas ─────────────────────────────────────────────────
export const accountQueryRequestSchema = z.object({
  /** Only the accounts linked to this user. */
  userId: z.string().optional(),
});

// ── Response Schemas ────────────────────────────────────────────────
/** A user who uses the account; `lastSeenAt` is that user's latest use. */
export const accountUserSchema = z.object({
  userId: z.string(),
  displayName: z.string(),
  source: AccountUserSourceSchema,
  lastSeenAt: z.string().nullish(),
});

export const accountListResponseSchema = z.object({
  id: z.string(),
  provider: AccountProviderSchema,
  accountEmail: z.string(),
  accountOrganizationUuid: z.string().nullish(),
  accountOrganizationName: z.string().nullish(),
  billingType: z.string().nullish(),
  organizationType: z.string().nullish(),
  seatTier: z.string().nullish(),
  /** The latest use by any user. */
  lastSeenAt: z.string().nullish(),
  users: z.array(accountUserSchema),
  /** Machines whose latest account is this one, retired ones included. */
  machines: z.number(),
  activeMachines: z.number(),
  /** The account's newest snapshot: state KNOWN or NO_SNAPSHOT, `sharedWith` empty. */
  usage: machineUsageResponseSchema,
});

export const accountDetailResponseSchema = accountListResponseSchema.extend({
  createdAt: z.string(),
  updatedAt: z.string().nullish(),
});

// ── Inferred Types ──────────────────────────────────────────────────
export type AccountQueryRequest = z.infer<typeof accountQueryRequestSchema>;
export type AccountUser = z.infer<typeof accountUserSchema>;
export type AccountListResponse = z.infer<typeof accountListResponseSchema>;
export type AccountDetailResponse = z.infer<typeof accountDetailResponseSchema>;
