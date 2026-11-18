import type {
  AppGrantCreateRequest,
  AppGrantFormValues,
  OrganizationFormValues,
  OrganizationGroupFormValues,
  OrganizationGroupRequest,
  OrganizationRequest,
} from "../schemas";

/** The backend rejects "" and whitespace-only on an optional field, so both become null. */
function blankToNull(value: string | undefined): string | null {
  return value?.trim() || null;
}

/**
 * Builds the create or update request body from the form values. Every field
 * is always present, because the update endpoint is a full update and treats
 * a missing field as null.
 */
export function toOrganizationRequest(values: OrganizationFormValues): OrganizationRequest {
  return {
    name: values.name.trim(),
    description: blankToNull(values.description),
    industry: blankToNull(values.industry),
    companySize: blankToNull(values.companySize),
    country: blankToNull(values.country),
    website: blankToNull(values.website),
  };
}

/**
 * Builds the create or rename request body of a group. The description is
 * always present, because the rename is a full update and treats a missing
 * description as null.
 */
export function toOrganizationGroupRequest(
  values: OrganizationGroupFormValues
): OrganizationGroupRequest {
  return {
    name: values.name.trim(),
    description: blankToNull(values.description),
  };
}

/**
 * Builds the grant request body. It carries the target as `userId` or as
 * `groupId`, never both, because the backend refuses a grant with two targets.
 */
export function toAppGrantRequest(values: AppGrantFormValues): AppGrantCreateRequest {
  return values.targetType === "USER"
    ? { app: values.app, userId: values.targetId }
    : { app: values.app, groupId: values.targetId };
}
