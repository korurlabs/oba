import { z } from "zod";

import { personName, PlatformOrganizationRoleSchema } from "@/lib/identity/schemas";

// ── Enum Schemas ───────────────────────────────────────────────────
export const MemberStatusSchema = z.enum(["ACTIVE", "DISABLED"]);
export type MemberStatus = z.infer<typeof MemberStatusSchema>;
export const MemberStatus = MemberStatusSchema.enum;

// ── Response Schemas ────────────────────────────────────────────────
/**
 * A member of the organization as the platform lists it, with the name Kaya
 * shows: the profile's first and last name, else the email.
 */
export const memberResponseSchema = z
  .object({
    /** The membership's id. */
    id: z.string(),
    userId: z.string(),
    email: z.string(),
    firstName: z.string().nullish(),
    lastName: z.string().nullish(),
    role: PlatformOrganizationRoleSchema,
    status: MemberStatusSchema,
  })
  .transform((member) => ({
    id: member.id,
    userId: member.userId,
    email: member.email,
    displayName: personName(member.firstName, member.lastName) ?? member.email,
    role: member.role,
    status: member.status,
  }));

// ── Inferred Types ──────────────────────────────────────────────────
export type MemberResponse = z.output<typeof memberResponseSchema>;
