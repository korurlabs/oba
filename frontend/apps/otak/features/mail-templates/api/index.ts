import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@oba/shared/schema";
import type {
  MailTemplateDetail,
  MailTemplateListApiResponse,
  MailTemplateQueryRequest,
  MailTemplateRequest,
} from "../schemas";

/** One page of the organization's mail templates, ordered by name, then language. */
export async function listMailTemplates(params: MailTemplateQueryRequest) {
  const res = await api.get<MailTemplateListApiResponse>(API_ROUTES.mailTemplates.list, {
    params,
  });
  return res.data;
}

/** One mail template with its subject and body; an unknown id answers `OTK_NOT_010`. */
export async function getMailTemplate(mailTemplateId: string) {
  const res = await api.get<ApiResponse<MailTemplateDetail>>(
    API_ROUTES.mailTemplates.detail(mailTemplateId)
  );
  return res.data;
}

/**
 * Creates a mail template. A missing name, subject or body is refused with
 * `GEN_VAL_001`, a name the organization already has in this language, case
 * ignored, with `OTK_STA_014`. ADMIN or OWNER only.
 */
export async function createMailTemplate(data: MailTemplateRequest) {
  const res = await api.post<ApiResponse<MailTemplateDetail>>(API_ROUTES.mailTemplates.list, data);
  return res.data;
}

/** Replaces every field, with the rules of a create. ADMIN or OWNER only. */
export async function updateMailTemplate(mailTemplateId: string, data: MailTemplateRequest) {
  const res = await api.put<ApiResponse<MailTemplateDetail>>(
    API_ROUTES.mailTemplates.detail(mailTemplateId),
    data
  );
  return res.data;
}

/** Removes the mail template; mails copied from it keep their text. ADMIN or OWNER only. */
export async function deleteMailTemplate(mailTemplateId: string): Promise<void> {
  await api.delete(API_ROUTES.mailTemplates.detail(mailTemplateId));
}
