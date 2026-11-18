/** The organization segment of a platform or app path, such as `/organizations/{id}/members` or `/kazan/organizations/{id}/security-board`. */
const ORGANIZATION_PATH_PATTERN = /\/organizations\/([^/?#]+)/;

/** The organization a request path names, or undefined when it names none. */
export function organizationIdFromPath(url: string | undefined): string | undefined {
  return url?.match(ORGANIZATION_PATH_PATTERN)?.[1];
}

/**
 * The `X-Organization-ID` a request sends, or undefined for none.
 *
 * The API refuses a request whose path and header name different
 * organizations, so the header follows the order a caller can rely on:
 * a value the caller set itself addresses one specific organization and is
 * kept; otherwise an organization in the path wins over the session's default
 * organization, because the user may be viewing an organization that is not
 * the default one.
 */
export function resolveOrganizationHeader(
  url: string | undefined,
  explicitOrganizationId: unknown,
  defaultOrganizationId: string | null | undefined,
): string | undefined {
  if (typeof explicitOrganizationId === "string" && explicitOrganizationId !== "") {
    return explicitOrganizationId;
  }
  const pathOrganizationId = organizationIdFromPath(url);
  if (pathOrganizationId !== undefined) {
    return pathOrganizationId;
  }
  if (defaultOrganizationId) {
    return defaultOrganizationId;
  }
  return undefined;
}
