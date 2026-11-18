/*
 * Pure rules of the Account tab, kept free of React Native so Bun can test them.
 */

/** The Appearance choices: follow the device, or always light or dark. */
export const APPEARANCES = ["system", "light", "dark"] as const;
export type Appearance = (typeof APPEARANCES)[number];

export function isAppearance(value: unknown): value is Appearance {
  return (APPEARANCES as readonly unknown[]).includes(value);
}

/**
 * Whether Settings lets the user delete their account. Account deletion is
 * turned off until it is offered again: the Delete account row stays visible
 * but dimmed and cannot be tapped, and it is the only way into the deletion.
 * Setting this to true turns the row and its confirmation back on.
 */
export const ACCOUNT_DELETION_OFFERED: boolean = false;

/**
 * The backend code that refuses `DELETE /users/me` while the user is the only
 * owner of an organization: disabling them would leave it without anyone who
 * can manage it.
 */
export const SOLE_OWNER_ERROR_CODE = "ORG_STA_003";

/** The line under the name on Account: the email and the active organization. */
export function accountSubtitle(
  email: string | undefined,
  organizationName: string | null
): string {
  return [email, organizationName].filter((part): part is string => !!part).join(" · ");
}

/**
 * The footer of Account: app name, version, build in parentheses and the
 * active organization, as in "Kazan 1.0 (24) · Korur". A missing part is left out.
 */
export function versionLabel(
  appName: string,
  version: string | null,
  build: string | null,
  organizationName: string | null
): string {
  const release: string = [appName, version, build ? `(${build})` : null]
    .filter((part): part is string => !!part)
    .join(" ");
  return organizationName ? `${release} · ${organizationName}` : release;
}

/**
 * The validation message of a profile field from a 422 `GEN_VAL_001`. The
 * backend names a nested field by its path (`profile.firstName`); the bare
 * name is accepted too.
 */
export function profileFieldError(
  fieldErrors: Record<string, string>,
  field: "firstName" | "lastName"
): string | null {
  return fieldErrors[`profile.${field}`] ?? fieldErrors[field] ?? null;
}
