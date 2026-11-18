import { KayaApiError, ERROR_CODES } from "./errors";

/**
 * Why the user can no longer use Kaya, as a refused call reports it: the
 * session ended (401), the account is disabled (403 GEN_SEC_002 with a user
 * status), or the user may no longer open the organization (a refusal of
 * KayaApiError.isNotMember, or a GEN_SEC_002 that names no draft proposer),
 * or Kaya is no longer granted to the user in the organization (403
 * APP_SEC_001). Any query or mutation can learn this first,
 * so it is kept here and the identity gate shows the matching page.
 */
export type AccessLoss = "signed-out" | "not-member" | "disabled" | "no-app";

let loss: AccessLoss | null = null;
const listeners = new Set<() => void>();

/** The loss a refused call reported, or null while access holds. */
export function accessLoss(): AccessLoss | null {
  return loss;
}

export function subscribeToAccessLoss(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/** What a refused call means for access; null when it is an ordinary failure for the caller. */
export function accessLossOf(error: unknown): AccessLoss | null {
  if (!(error instanceof KayaApiError)) {
    return null;
  }
  if (error.status === 401) {
    return "signed-out";
  }
  if (error.isNotMember) {
    return "not-member";
  }
  if (error.status === 403 && error.code === ERROR_CODES.APP_ACCESS_DENIED) {
    return "no-app";
  }
  if (error.status === 403 && error.code === ERROR_CODES.USER_DISABLED) {
    // GEN_SEC_002 is the tenant check's refusal: a user status names a
    // disabled account, anything else a user who is no member of the
    // organization. A refused action of one user answers GEN_SEC_003. A
    // write to another member's draft answers GEN_SEC_002 with the draft's
    // proposer; the member keeps access, so the caller shows that refusal.
    if (error.errorValues.proposedBy !== undefined) return null;
    return error.errorValues.userStatus !== undefined ? "disabled" : "not-member";
  }
  return null;
}

/** Records the access loss a refused call reports; other errors are left to their caller. */
export function reportAccessLoss(error: unknown): void {
  const next = accessLossOf(error);
  if (next === null || next === loss) {
    return;
  }
  loss = next;
  listeners.forEach((listener: () => void) => listener());
}
