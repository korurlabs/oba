/** The error codes of a failed check that have their own explanation in the locale files. */
export const KNOWN_AZURE_CHECK_ERRORS = [
  "AZR_UPS_005",
  "AZR_UPS_006",
  "AZR_UPS_007",
  "AZR_UPS_008",
  "AZR_UPS_009",
  "AZR_STA_005",
] as const;

export type KnownAzureCheckError = (typeof KNOWN_AZURE_CHECK_ERRORS)[number];

export interface AzureLastError {
  /** The Oba error code, for example `AZR_UPS_009`. */
  code: string;
  /** Entra's AADSTS code, sent only with `AZR_UPS_009`; null otherwise. */
  entraCode: string | null;
  /** True when the code has its own explanation under `integrations.azure.lastErrors`. */
  known: boolean;
}

/**
 * Splits a stored `lastError` into the Oba code and Entra's code. The API
 * writes the Oba code first and adds ":" and the AADSTS code only when Entra
 * sent one, so everything before the first colon is the Oba code.
 */
export function parseAzureLastError(lastError: string | null | undefined): AzureLastError | null {
  const value = lastError?.trim();
  if (!value) return null;

  const separator = value.indexOf(":");
  const code = separator === -1 ? value : value.slice(0, separator);
  const entraCode = separator === -1 ? null : value.slice(separator + 1) || null;
  const known = (KNOWN_AZURE_CHECK_ERRORS as readonly string[]).includes(code);
  return { code, entraCode, known };
}
