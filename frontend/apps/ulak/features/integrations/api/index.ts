import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@oba/shared/schema";
import type { AzureConnection, AzureConnectionRequest } from "../schemas";

/**
 * The Azure connection endpoints name no organization in their path, so each
 * call sets the header itself: the viewed organization need not be the default one.
 */
function organizationHeader(organizationId: string) {
  return { headers: { "X-Organization-ID": organizationId } };
}

export async function listAzureConnections(organizationId: string) {
  const res = await api.get<ApiResponse<AzureConnection[]>>(
    API_ROUTES.azureConnections.list,
    organizationHeader(organizationId)
  );
  return res.data;
}

export async function getAzureConnection(organizationId: string, connectionId: string) {
  const res = await api.get<ApiResponse<AzureConnection>>(
    API_ROUTES.azureConnections.detail(connectionId),
    organizationHeader(organizationId)
  );
  return res.data;
}

export async function createAzureConnection(organizationId: string, data: AzureConnectionRequest) {
  const res = await api.post<ApiResponse<AzureConnection>>(
    API_ROUTES.azureConnections.list,
    data,
    organizationHeader(organizationId)
  );
  return res.data;
}

export async function updateAzureConnection(
  organizationId: string,
  connectionId: string,
  data: AzureConnectionRequest
) {
  const res = await api.put<ApiResponse<AzureConnection>>(
    API_ROUTES.azureConnections.detail(connectionId),
    data,
    organizationHeader(organizationId)
  );
  return res.data;
}

export async function deleteAzureConnection(
  organizationId: string,
  connectionId: string
): Promise<void> {
  await api.delete(
    API_ROUTES.azureConnections.detail(connectionId),
    organizationHeader(organizationId)
  );
}

/**
 * Checks the connection against Azure now. The answer is the stored result,
 * also when Azure refused: the status and `lastError` say how it went.
 */
export async function verifyAzureConnection(organizationId: string, connectionId: string) {
  const res = await api.post<ApiResponse<AzureConnection>>(
    API_ROUTES.azureConnections.verify(connectionId),
    undefined,
    organizationHeader(organizationId)
  );
  return res.data;
}
