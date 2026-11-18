import { useMutation, useQueryClient } from "@tanstack/react-query";

import type { TicketCreateBody } from "@/components/ticket/newTicketModel";
import type { TicketRef } from "@/hooks/queries/ticketDetail";
import { invalidateTicketViews, ticketKeys } from "@/hooks/queries/tickets";
import { api, readData } from "@/lib/api";
import { API_ROUTES } from "@/lib/apiRoutes";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import { ticketDetailSchema, type TicketDetail } from "@/lib/schemas/tickets";

/** A create request and the board it goes to. */
export interface TicketCreate {
  boardId: string;
  body: TicketCreateBody;
}

/**
 * `POST /kazan/boards/{boardId}/tickets`. Sent with `_silentErrors`: New
 * ticket, the ticket screen in create mode, shows a refused field under its
 * row and any other failure as a toast with Retry, so the app-wide toast
 * would say it twice. The created
 * ticket is put in the cache under its key, so its screen opens with it.
 */
export function useCreateTicket() {
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation<TicketDetail, Error, TicketCreate>({
    mutationFn: async ({ boardId, body }) => {
      const response = await api.post(API_ROUTES.tickets.list(boardId), body, {
        _silentErrors: true,
      });
      return readData(ticketDetailSchema, response.data);
    },
    onSuccess: (ticket) => {
      if (!orgId) return;
      queryClient.setQueryData<TicketRef>(ticketKeys.byKey(orgId, ticket.key), {
        ticketId: ticket.id,
        boardId: ticket.board.id,
      });
      queryClient.setQueryData<TicketDetail>(
        ticketKeys.detail(orgId, ticket.board.id, ticket.id),
        ticket
      );
    },
    onError: (error) => console.warn("[ticket] create failed", error.message),
    // The board lists, My Tickets, Home and a parent's sub-tickets all show the new ticket.
    onSettled: () => invalidateTicketViews(queryClient, orgId),
  });
}
