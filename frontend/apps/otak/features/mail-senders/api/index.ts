import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@oba/shared/schema";
import type { MailSenderListApiResponse, MailSenderListItem, MailSenderRequest } from "../schemas";

/** Every mail sender of the organization, ordered by mail address. */
export async function listMailSenders() {
  const res = await api.get<MailSenderListApiResponse>(API_ROUTES.mailSenders.list);
  return res.data;
}

/**
 * Adds a sender once the server has found its mailbox of the given type in
 * the Microsoft 365 tenant. A second sender with the same address, case
 * ignored, is refused with `OTK_STA_008`. A personal address with no account
 * in the tenant is refused with `OTK_VAL_002` and an account without a
 * mailbox with `OTK_VAL_003`; a shared address that no member account has as
 * its mail address is refused with `OTK_VAL_004`. ADMIN or OWNER only.
 */
export async function createMailSender(data: MailSenderRequest) {
  const res = await api.post<ApiResponse<MailSenderListItem>>(API_ROUTES.mailSenders.list, data);
  return res.data;
}

/** Removes the sender. ADMIN or OWNER only. */
export async function deleteMailSender(mailSenderId: string): Promise<void> {
  await api.delete(API_ROUTES.mailSenders.detail(mailSenderId));
}
