import { z } from "zod";

// ── Enums ───────────────────────────────────────────────────────────

export const InvitationDecisionSchema = z.enum(["ACCEPT", "DECLINE"]);
export type InvitationDecision = z.infer<typeof InvitationDecisionSchema>;
export const InvitationDecision = InvitationDecisionSchema.enum;

// ── Request Types ───────────────────────────────────────────────────

export const InvitationDecisionRequestSchema = z.object({
  invitationId: z.string(),
  decision: InvitationDecisionSchema,
});
export type InvitationDecisionRequest = z.infer<
  typeof InvitationDecisionRequestSchema
>;
