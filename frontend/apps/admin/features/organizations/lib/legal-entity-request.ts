import type { LegalEntityDetail, LegalEntityFormValues, LegalEntityRequest } from "../schemas";

/** The backend refuses "" and whitespace-only on an optional field, so both become null. */
function blankToNull(value: string | null | undefined): string | null {
  return value?.trim() || null;
}

/**
 * Builds the create or update request body from the form values. Every field
 * is always present, because the update is a full replace and treats a
 * missing field as null.
 */
export function toLegalEntityRequest(values: LegalEntityFormValues): LegalEntityRequest {
  return {
    shortName: values.shortName.trim(),
    legalName: values.legalName.trim(),
    country: values.country.trim(),
    registrationNumber: blankToNull(values.registrationNumber),
    vatNumber: blankToNull(values.vatNumber),
    street: blankToNull(values.street),
    postalCode: blankToNull(values.postalCode),
    city: blankToNull(values.city),
    governingLaw: blankToNull(values.governingLaw),
    court: blankToNull(values.court),
  };
}

/** The form values of a stored legal entity, or an empty form without one. */
export function toLegalEntityFormValues(
  entity: LegalEntityDetail | null | undefined
): LegalEntityFormValues {
  return {
    shortName: entity?.shortName ?? "",
    legalName: entity?.legalName ?? "",
    country: entity?.country ?? "",
    registrationNumber: entity?.registrationNumber ?? "",
    vatNumber: entity?.vatNumber ?? "",
    street: entity?.street ?? "",
    postalCode: entity?.postalCode ?? "",
    city: entity?.city ?? "",
    governingLaw: entity?.governingLaw ?? "",
    court: entity?.court ?? "",
  };
}
