/**
 * The page to return to after signing in, or null when the value is not a
 * path on this site. Only same-site paths are followed, so a crafted sign-in
 * link cannot send the user elsewhere.
 */
export function safeCallbackPath(value: string | null): string | null {
  if (
    value === null ||
    !value.startsWith("/") ||
    value.startsWith("//") ||
    value.startsWith("/\\")
  ) {
    return null;
  }
  return value;
}
