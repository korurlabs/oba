import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@oba/shared/schema";
import type { BacklogDetailResponse } from "../schemas";

export async function getBacklog(boardId: string) {
  const res = await api.get<ApiResponse<BacklogDetailResponse>>(
    API_ROUTES.backlog.detail(boardId),
  );
  return res.data;
}

export async function addTicketToBacklog(
  boardId: string,
  ticketId: string,
): Promise<void> {
  await api.post(API_ROUTES.backlog.addTicket(boardId, ticketId));
}
