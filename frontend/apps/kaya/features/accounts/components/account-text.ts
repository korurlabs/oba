import type { AccountListResponse } from "../schemas";

/** The usage text of an account whose provider has no recorder snapshot. */
export const NOT_REPORTED_FOR_PROVIDER = "Not reported for this provider";

/** The text for a subscription value the recorder never sent. */
export const NOT_REPORTED = "Not reported";

/** The seat tier, else the billing type, else "Not reported". */
export function tierText(account: Pick<AccountListResponse, "seatTier" | "billingType">): string {
  return account.seatTier || account.billingType || NOT_REPORTED;
}

/** The provider organization's name; an account without one is a personal account. */
export function organizationText(
  account: Pick<AccountListResponse, "accountOrganizationName">
): string {
  return account.accountOrganizationName || "Personal";
}
