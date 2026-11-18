import type { MachineAccount, MachineResponse } from "../schemas";

/** The text for a value the machine never sent. */
export const NOT_REPORTED = "Not reported";

/** "Korur · max · claude_max_20x"-style text of the account's subscription, or null. */
export function accountSubscriptionText(account: MachineAccount | null | undefined): string | null {
  if (account === null || account === undefined) return null;
  const parts = [account.accountOrganizationName, account.billingType, account.seatTier].filter(
    (part): part is string => typeof part === "string" && part !== ""
  );
  return parts.length === 0 ? null : parts.join(" · ");
}

/** The command a developer runs, in any directory, to store a new recorder token. */
export function tokenCommand(token: string): string {
  return `kagi secrets set --personal --path /kaya/recorder KAYA_RECORDER_TOKEN=${token}`;
}

/**
 * What a user may do to a machine. "Self" is the machine's user being the
 * signed-in user. Rotation hands out a plaintext token, so only the machine's
 * own user rotates; that user or an admin revokes and retires.
 */
export function machinePermissions(
  machine: Pick<MachineResponse, "userId" | "status" | "token">,
  myUserId: string | null | undefined,
  isAdmin: boolean
) {
  const self = myUserId !== null && myUserId !== undefined && machine.userId === myUserId;
  const active = machine.status === "ACTIVE";
  return {
    rotate: self && active,
    revoke: (self || isAdmin) && active && machine.token !== null && machine.token !== undefined,
    retire: (self || isAdmin) && active,
  };
}
