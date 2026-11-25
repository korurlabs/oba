import { isApiError } from "@oba/shared/api/axios";
import { CHECK_ERROR_CODES } from "../schemas";

type CheckErrorKey = keyof typeof CHECK_ERROR_CODES;

const KEY_BY_CODE: ReadonlyMap<string, CheckErrorKey> = new Map(
  (Object.entries(CHECK_ERROR_CODES) as [CheckErrorKey, string][]).map(([key, code]) => [code, key])
);

/**
 * The `cyberscore.errors` key of a refused check write, or null for any other
 * failure, which the caller reports with the server's own message.
 */
export function checkErrorKey(error: unknown): CheckErrorKey | null {
  if (!isApiError(error) || !error.code) return null;
  return KEY_BY_CODE.get(error.code) ?? null;
}

/** True when the check the write was aimed at no longer exists. */
export function isCheckNotFound(error: unknown): boolean {
  return isApiError(error, CHECK_ERROR_CODES.notFound);
}
