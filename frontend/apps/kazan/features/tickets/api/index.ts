import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import { filterEmptyValues } from "@/lib/filter-utils";
import type { ApiResponse } from "@oba/shared/schema";
import type {
  TicketCreateRequest,
  TicketUpdateRequest,
  TicketMoveRequest,
  TicketOrderUpdateRequest,
  TicketBulkMoveRequest,
  TicketBulkMoveResponse,
  TicketDetailResponse,
  TicketListResponse,
} from "../schemas";

export async function createTicket(
  boardId: string,
  data: TicketCreateRequest,
) {
  const res = await api.post<ApiResponse<TicketDetailResponse>>(
    API_ROUTES.tickets.list(boardId),
    data,
  );
  return res.data;
}

export async function listTickets(
  boardId: string,
  params?: Record<string, unknown>,
) {
  const filtered = filterEmptyValues(params);
  // Serialize array values as comma-separated strings for the backend
  const serialized = filtered
    ? Object.fromEntries(
        Object.entries(filtered).map(([key, value]) => [
          key,
          Array.isArray(value) ? value.join(",") : value,
        ]),
      )
    : undefined;
  const res = await api.get<ApiResponse<TicketListResponse[]>>(
    API_ROUTES.tickets.list(boardId),
    { params: serialized },
  );
  return res.data;
}

export async function getTicketById(boardId: string, ticketId: string) {
  const res = await api.get<ApiResponse<TicketDetailResponse>>(
    API_ROUTES.tickets.detail(boardId, ticketId),
  );
  return res.data;
}

export async function updateTicket(
  boardId: string,
  ticketId: string,
  data: TicketUpdateRequest,
) {
  const res = await api.put<ApiResponse<TicketDetailResponse>>(
    API_ROUTES.tickets.detail(boardId, ticketId),
    data,
  );
  return res.data;
}

export async function deleteTicket(
  boardId: string,
  ticketId: string,
): Promise<void> {
  await api.delete(API_ROUTES.tickets.detail(boardId, ticketId));
}

/** Restores a soft-deleted ticket together with the sub-tickets its delete took along. */
export async function restoreTicket(
  boardId: string,
  ticketId: string,
): Promise<void> {
  await api.post(API_ROUTES.tickets.restore(boardId, ticketId));
}

export async function moveTicket(
  boardId: string,
  ticketId: string,
  data: TicketMoveRequest,
) {
  const res = await api.post<ApiResponse<TicketDetailResponse>>(
    API_ROUTES.tickets.move(boardId, ticketId),
    data,
  );
  return res.data;
}

export async function updateTicketOrder(
  boardId: string,
  ticketId: string,
  data: TicketOrderUpdateRequest,
) {
  const res = await api.put<ApiResponse<TicketDetailResponse>>(
    API_ROUTES.tickets.order(boardId, ticketId),
    data,
  );
  return res.data;
}

export async function bulkMoveTickets(boardId: string, data: TicketBulkMoveRequest) {
  const res = await api.post<ApiResponse<TicketBulkMoveResponse>>(
    API_ROUTES.tickets.bulkMove(boardId),
    data,
  );
  return res.data;
}

