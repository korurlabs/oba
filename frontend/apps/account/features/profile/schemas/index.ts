import { z } from "zod";

// ── Form Schema ─────────────────────────────────────────────────────

/**
 * Limits of the three fields the profile form edits. They mirror
 * `userProfileUpdateSchema` in `@oba/shared/features/users/schema`
 * (`firstName` 255, `lastName` 255, `phoneNumber` 30) and are also set as
 * `maxLength` on the inputs, so the limit cannot be exceeded by typing.
 */
export const PROFILE_FIELD_MAX_LENGTH = {
  firstName: 255,
  lastName: 255,
  phoneNumber: 30,
} as const;

/**
 * The profile form works on plain strings (an empty input is ""), while the
 * API shape is nullable. `use-update-profile.ts` converts between the two.
 */
export const profileFormSchema = z.object({
  firstName: z.string().max(PROFILE_FIELD_MAX_LENGTH.firstName),
  lastName: z.string().max(PROFILE_FIELD_MAX_LENGTH.lastName),
  phoneNumber: z.string().max(PROFILE_FIELD_MAX_LENGTH.phoneNumber),
});

// ── Inferred Types ──────────────────────────────────────────────────

export type ProfileFormValues = z.infer<typeof profileFormSchema>;
