import type { SignOutOutcome } from "@/contexts/AuthContext";

/**
 * The warning a finished sign-out shows, or null. Only a failed push
 * unregistration needs one: the phone has forgotten its device id either way,
 * but the server may still send this account's notifications to it. A grant
 * that could not be revoked needs none, because the tokens are gone from the
 * phone and the grant expires on its own.
 */
export function signOutWarningKey(
  outcome: SignOutOutcome
): "authSignOutPushStillRegistered" | null {
  return outcome.pushDeviceUnregistered === false ? "authSignOutPushStillRegistered" : null;
}
