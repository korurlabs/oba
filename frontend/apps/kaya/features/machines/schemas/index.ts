import { z } from "zod";

import { AccountProviderSchema } from "@/features/accounts/schemas/provider";

// ── Enum Schemas ───────────────────────────────────────────────────
export const MachineStatusSchema = z.enum(["ACTIVE", "RETIRED"]);
export type MachineStatus = z.infer<typeof MachineStatusSchema>;
export const MachineStatus = MachineStatusSchema.enum;

export const MachineUsageStateSchema = z.enum(["KNOWN", "NO_ACCOUNT", "NO_SNAPSHOT"]);
export type MachineUsageState = z.infer<typeof MachineUsageStateSchema>;
export const MachineUsageState = MachineUsageStateSchema.enum;

// ── Request Schemas ─────────────────────────────────────────────────
/** `POST /machines`: the machine's host name; `userId` only when an admin registers it for someone else. */
export const machineCreateRequestSchema = z.object({
  machineName: z
    .string()
    .trim()
    .min(1, "Enter the machine name.")
    .max(255, "A machine name has at most 255 characters."),
  userId: z.string().optional(),
});

export const machineQueryRequestSchema = z.object({
  userId: z.string().optional(),
  /** Only machines whose latest account is this one. */
  accountId: z.string().optional(),
  status: MachineStatusSchema.optional(),
});

// ── Response Schemas ────────────────────────────────────────────────
export const machineAccountSchema = z.object({
  id: z.string(),
  provider: AccountProviderSchema,
  accountEmail: z.string().nullish(),
  accountOrganizationUuid: z.string().nullish(),
  accountOrganizationName: z.string().nullish(),
  billingType: z.string().nullish(),
  organizationType: z.string().nullish(),
  seatTier: z.string().nullish(),
});

export const machineTokenSchema = z.object({
  id: z.string(),
  tokenPrefix: z.string(),
  createdAt: z.string(),
  lastUsedAt: z.string().nullish(),
  expiresAt: z.string(),
});

/** One usage window; `usedPercentage` is null once `resetsAt` has passed (`reset`). */
export const machineUsageWindowSchema = z.object({
  /** A decimal such as 23.5; read as a string too, in case the server writes decimals as text. */
  usedPercentage: z.union([z.number(), z.string()]).nullish(),
  resetsAt: z.string(),
  reset: z.boolean(),
});

export const machineUsageSharedSchema = z.object({
  machineId: z.string(),
  machineName: z.string(),
});

export const machineUsageResponseSchema = z.object({
  state: MachineUsageStateSchema,
  capturedAt: z.string().nullish(),
  reportedByMachineId: z.string().nullish(),
  reportedByMachineName: z.string().nullish(),
  sharedWith: z.array(machineUsageSharedSchema),
  fiveHour: machineUsageWindowSchema.nullish(),
  sevenDay: machineUsageWindowSchema.nullish(),
});

export const machineResponseSchema = z.object({
  id: z.string(),
  userId: z.string(),
  userDisplayName: z.string().nullish(),
  machineName: z.string(),
  claudeCodeVersion: z.string().nullish(),
  account: machineAccountSchema.nullish(),
  status: MachineStatusSchema,
  lastSeenAt: z.string().nullish(),
  token: machineTokenSchema.nullish(),
  usage: machineUsageResponseSchema.nullish(),
  sessionCount: z.number(),
  createdAt: z.string(),
  updatedAt: z.string().nullish(),
});

export const machineCreatedResponseSchema = z.object({
  machine: machineResponseSchema,
  token: z.string(),
});

export const machineTokenCreatedResponseSchema = z.object({
  token: z.string(),
  tokenId: z.string(),
  tokenPrefix: z.string(),
  expiresAt: z.string(),
});

// ── Inferred Types ──────────────────────────────────────────────────
export type MachineCreateRequest = z.infer<typeof machineCreateRequestSchema>;
export type MachineQueryRequest = z.infer<typeof machineQueryRequestSchema>;
export type MachineAccount = z.infer<typeof machineAccountSchema>;
export type MachineToken = z.infer<typeof machineTokenSchema>;
export type MachineUsageWindow = z.infer<typeof machineUsageWindowSchema>;
export type MachineUsageShared = z.infer<typeof machineUsageSharedSchema>;
export type MachineUsageResponse = z.infer<typeof machineUsageResponseSchema>;
export type MachineResponse = z.infer<typeof machineResponseSchema>;
export type MachineCreatedResponse = z.infer<typeof machineCreatedResponseSchema>;
export type MachineTokenCreatedResponse = z.infer<typeof machineTokenCreatedResponseSchema>;
