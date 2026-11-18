import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@oba/shared/schema";
import type { ContactDetail, ContactListItem, ContactRequest } from "../schemas";

/** Every contact of the relation, the main contact first, then by name; not paged. */
export async function listContacts(relationId: string) {
  const res = await api.get<ApiResponse<ContactListItem[]>>(API_ROUTES.contacts.list(relationId));
  return res.data;
}

export async function createContact(relationId: string, data: ContactRequest) {
  const res = await api.post<ApiResponse<ContactDetail>>(
    API_ROUTES.contacts.list(relationId),
    data
  );
  return res.data;
}

/** A full replace: a null or missing optional field is cleared. */
export async function updateContact(relationId: string, contactId: string, data: ContactRequest) {
  const res = await api.put<ApiResponse<ContactDetail>>(
    API_ROUTES.contacts.detail(relationId, contactId),
    data
  );
  return res.data;
}

export async function deleteContact(relationId: string, contactId: string): Promise<void> {
  await api.delete(API_ROUTES.contacts.detail(relationId, contactId));
}
