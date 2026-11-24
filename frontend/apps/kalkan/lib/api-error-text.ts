import { isApiError } from "@oba/shared/api/axios";

/**
 * The text a failed request shows: the translated text `textKeys` names for
 * the error code, else the server's message, else `fallbackKey`. `t` is the
 * translator the keys belong to.
 */
export function apiErrorText(
  error: unknown,
  t: (key: string) => string,
  fallbackKey: string,
  textKeys: Partial<Record<string, string>>
): string {
  const textKey = isApiError(error) && error.code !== undefined ? textKeys[error.code] : undefined;
  if (textKey !== undefined) {
    return t(textKey);
  }
  if (error instanceof Error && error.message !== "") {
    return error.message;
  }
  return t(fallbackKey);
}

/**
 * The fields a refused save names with the server's message for each: the
 * `errorValues` of a 422. `fieldAliases` maps a server property to the form
 * field that shows it, such as a class-level check on a request record.
 */
export function validationFieldErrors(
  error: unknown,
  fieldAliases: Partial<Record<string, string>> = {}
): Record<string, string> {
  if (!isApiError(error, "GEN_VAL_001")) {
    return {};
  }
  return Object.fromEntries(
    Object.entries(error.errorValues)
      .filter((entry): entry is [string, string] => typeof entry[1] === "string")
      .map(([field, message]) => [fieldAliases[field] ?? field, message])
  );
}
