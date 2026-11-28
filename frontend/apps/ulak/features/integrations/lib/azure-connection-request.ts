import type {
  AzureConnection,
  AzureConnectionFormValues,
  AzureConnectionRequest,
} from "../schemas";

/**
 * Builds the create or update request body from the form values. The client
 * id is always present, because the update is a full replace and treats null
 * as "clear it".
 */
export function toAzureConnectionRequest(
  values: AzureConnectionFormValues
): AzureConnectionRequest {
  return {
    name: values.name.trim(),
    tenantId: values.tenantId.trim(),
    clientId: values.clientId.trim() || null,
  };
}

/** The form values of a stored connection, or an empty form without one. */
export function toAzureConnectionFormValues(
  connection: AzureConnection | null | undefined
): AzureConnectionFormValues {
  return {
    name: connection?.name ?? "",
    tenantId: connection?.tenantId ?? "",
    clientId: connection?.clientId ?? "",
  };
}
