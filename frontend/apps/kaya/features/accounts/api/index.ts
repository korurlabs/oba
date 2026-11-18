import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@/types/api";

import type { AccountDetailResponse, AccountListResponse, AccountQueryRequest } from "../schemas";

/** Every account of the organization, by account email and organization name; not paged. */
export async function listAccounts(params: AccountQueryRequest) {
  const res = await api.get<ApiResponse<AccountListResponse[]>>(API_ROUTES.accounts.list, {
    params,
  });
  return res.data;
}

export async function getAccount(id: string) {
  const res = await api.get<ApiResponse<AccountDetailResponse>>(API_ROUTES.accounts.detail(id));
  return res.data;
}
