import type { MachineAccount } from "../schemas";

/**
 * The billing types Claude Code reports for an account, as readable labels.
 * The recorder copies the value verbatim and the server stores any text, so a
 * value missing here is shown through `humanize`, never raw.
 */
export const BILLING_TYPE_LABELS: ReadonlyMap<string, string> = new Map([
  ["stripe_subscription", "Subscription"],
  ["stripe_subscription_contracted", "Contracted subscription"],
  ["stripe_subscription_enterprise_self_serve", "Enterprise self-serve subscription"],
  ["apple_subscription", "Apple subscription"],
  ["google_play_subscription", "Google Play subscription"],
  ["aws_marketplace", "AWS Marketplace"],
  ["c4e_consumption_trial", "Consumption trial"],
  ["usage_based", "Usage-based"],
]);

/** "max_5x" as "Max 5x": separators become spaces and the first letter a capital. */
export function humanize(value: string): string {
  const words = value.replace(/[_-]+/g, " ").replace(/\s+/g, " ").trim();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/** The billing type's label, else the value humanized; null when none was reported. */
export function billingTypeLabel(billingType: string | null | undefined): string | null {
  if (billingType === null || billingType === undefined || billingType.trim() === "") return null;
  return BILLING_TYPE_LABELS.get(billingType) ?? humanize(billingType);
}

/**
 * The second line of the Account cell: the Claude organization, the billing
 * type and the seat tier, readable and joined, as "Acme · Subscription · Max 5x";
 * null when the account reported none of them.
 */
export function accountPlanText(account: MachineAccount | null | undefined): string | null {
  if (account === null || account === undefined) return null;
  const seatTier = account.seatTier?.trim() ? humanize(account.seatTier) : null;
  const parts = [
    account.accountOrganizationName?.trim() || null,
    billingTypeLabel(account.billingType),
    seatTier,
  ].filter((part): part is string => part !== null);
  return parts.length === 0 ? null : parts.join(" · ");
}
