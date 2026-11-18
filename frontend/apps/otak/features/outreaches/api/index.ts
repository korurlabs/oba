import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@oba/shared/schema";
import type {
  OutreachDetail,
  OutreachListApiResponse,
  OutreachQueryRequest,
  OutreachRequest,
  OutreachSendRequest,
  OutreachUpdateRequest,
} from "../schemas";

/** One page of the organization's outreaches, newest first; `relationId` narrows it to one relation. */
export async function listOutreaches(params: OutreachQueryRequest) {
  const res = await api.get<OutreachListApiResponse>(API_ROUTES.outreaches.list, { params });
  return res.data;
}

export async function getOutreach(outreachId: string) {
  const res = await api.get<ApiResponse<OutreachDetail>>(API_ROUTES.outreaches.detail(outreachId));
  return res.data;
}

/**
 * Stores a DRAFT on its channel; a MAIL outreach with its own mail, carrying
 * a copy of the PDF of each of `documentTemplateIds`. An unknown mail
 * template answers `OTK_NOT_010`, an unknown document template
 * `OTK_NOT_007`, one without its PDF `OTK_VAL_012`. ADMIN or OWNER only.
 */
export async function createOutreach(data: OutreachRequest) {
  const res = await api.post<ApiResponse<OutreachDetail>>(API_ROUTES.outreaches.list, data);
  return res.data;
}

/**
 * Replaces the contact, the title and, for MAIL, the mail; a SENT outreach
 * answers `OTK_STA_011`. ADMIN or OWNER only.
 */
export async function updateOutreach(outreachId: string, data: OutreachUpdateRequest) {
  const res = await api.put<ApiResponse<OutreachDetail>>(
    API_ROUTES.outreaches.detail(outreachId),
    data
  );
  return res.data;
}

/**
 * Sends the mail from the chosen sender. A Microsoft Graph failure leaves the
 * outreach FAILED and answers `NTF_UPS_001`; any channel but MAIL answers
 * `OTK_VAL_014`. ADMIN or OWNER only.
 */
export async function sendOutreach(outreachId: string, data: OutreachSendRequest) {
  const res = await api.post<ApiResponse<OutreachDetail>>(
    API_ROUTES.outreaches.send(outreachId),
    data
  );
  return res.data;
}

/**
 * Records a WHATSAPP or CALL outreach as done: SENT now, to the contact's
 * phone. A MAIL outreach answers `OTK_VAL_014`, a contact without phone
 * `OTK_VAL_015`, a SENT outreach `OTK_STA_011`. ADMIN or OWNER only.
 */
export async function completeOutreach(outreachId: string) {
  const res = await api.post<ApiResponse<OutreachDetail>>(
    API_ROUTES.outreaches.complete(outreachId)
  );
  return res.data;
}

/** Removes the outreach, its mail and the mail's attachments. ADMIN or OWNER only. */
export async function deleteOutreach(outreachId: string): Promise<void> {
  await api.delete(API_ROUTES.outreaches.detail(outreachId));
}
