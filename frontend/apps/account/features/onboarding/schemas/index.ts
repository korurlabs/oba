import { z } from "zod";

// ── Request Schemas ─────────────────────────────────────────────────

/**
 * Body of `POST onboarding/complete`. The organization fields are optional
 * here on purpose: an invited user can finish onboarding without creating
 * an organization, so the request legitimately carries `name: undefined`.
 */
export const onboardingCompleteRequestSchema = z.object({
  firstName: z.string().min(1).max(255),
  lastName: z.string().min(1).max(255),
  jobTitle: z.string().max(100).optional(),
  name: z.string().max(255).optional(),
  industry: z.string().max(100).optional(),
  companySize: z.string().max(50).optional(),
  country: z.string().max(3).optional(),
  website: z.string().max(512).optional(),
});

// ── Form Schemas ────────────────────────────────────────────────────

/**
 * Limits of the text inputs of the onboarding form. They are also set as
 * `maxLength` on the inputs, so the limit cannot be exceeded by typing.
 */
export const ONBOARDING_FIELD_MAX_LENGTH = {
  firstName: 255,
  lastName: 255,
  jobTitle: 100,
  name: 255,
  website: 512,
} as const;

interface OnboardingStepperSchemaOptions {
  /**
   * Set on the self-register path, where the organization name is required.
   * Left out on the invited path, where the name is optional.
   */
  organizationNameRequiredMessage?: string;
}

/** Full stepper form schema (superset, used as the single useForm schema) */
export const createOnboardingStepperSchema = (
  t: (key: string) => string,
  { organizationNameRequiredMessage }: OnboardingStepperSchemaOptions = {},
) =>
  z.object({
    firstName: z
      .string()
      .trim()
      .min(1, t("firstNameRequired"))
      .max(ONBOARDING_FIELD_MAX_LENGTH.firstName),
    lastName: z
      .string()
      .trim()
      .min(1, t("lastNameRequired"))
      .max(ONBOARDING_FIELD_MAX_LENGTH.lastName),
    jobTitle: z
      .string()
      .max(ONBOARDING_FIELD_MAX_LENGTH.jobTitle)
      .optional()
      .or(z.literal("")),
    name: z
      .string()
      .max(ONBOARDING_FIELD_MAX_LENGTH.name)
      .optional()
      .or(z.literal(""))
      .refine(
        (value) =>
          organizationNameRequiredMessage === undefined ||
          (value ?? "").trim().length > 0,
        { message: organizationNameRequiredMessage },
      ),
    industry: z.string().max(100).optional().or(z.literal("")),
    companySize: z.string().max(50).optional().or(z.literal("")),
    country: z.string().max(3).optional().or(z.literal("")),
    website: z
      .string()
      .max(ONBOARDING_FIELD_MAX_LENGTH.website)
      .optional()
      .or(z.literal("")),
  });

// ── Response Schemas ────────────────────────────────────────────────

export const onboardingStatusResponseSchema = z.object({
  status: z.string(),
  userId: z.string().optional(),
  organizationId: z.string().optional(),
});

// ── Inferred Types ──────────────────────────────────────────────────

export type OnboardingCompleteRequest = z.infer<
  typeof onboardingCompleteRequestSchema
>;
export type OnboardingStepperFormValues = z.infer<
  ReturnType<typeof createOnboardingStepperSchema>
>;
export type OnboardingStatusResponse = z.infer<
  typeof onboardingStatusResponseSchema
>;
