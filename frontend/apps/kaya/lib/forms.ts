import { z } from "zod";

/**
 * A reason recorded with an action, such as a review or an override. The
 * limits equal the server's; the server stays the authority.
 */
export const reasonSchema = z
  .string()
  .trim()
  .min(10, "Give a reason of at least 10 characters.")
  .max(1000, "A reason has at most 1000 characters.");

/** The first message of a failed parse, for a form's error line. */
export function firstIssue(error: z.ZodError): string {
  return error.issues[0]?.message ?? "Check the values entered.";
}
