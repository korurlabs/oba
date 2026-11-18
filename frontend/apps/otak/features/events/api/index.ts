import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@oba/shared/schema";
import type {
  EventDetail,
  EventListApiResponse,
  EventQueryRequest,
  EventRequest,
} from "../schemas";

/**
 * One page of the organization's events. With a period the server needs the
 * viewer's `today`: upcoming by start date soonest first, past by start date
 * most recent first. Without one it lists every event, most recent start first.
 */
export async function listEvents(params: EventQueryRequest) {
  const res = await api.get<EventListApiResponse>(API_ROUTES.events.list, { params });
  return res.data;
}

export async function getEvent(eventId: string) {
  const res = await api.get<ApiResponse<EventDetail>>(API_ROUTES.events.detail(eventId));
  return res.data;
}

/**
 * A second event with the same name, case ignored, is refused with
 * `OTK_STA_013`; an end before the start with `OTK_VAL_010`.
 */
export async function createEvent(data: EventRequest) {
  const res = await api.post<ApiResponse<EventDetail>>(API_ROUTES.events.list, data);
  return res.data;
}

/** A full replace: a null optional field is cleared. */
export async function updateEvent(eventId: string, data: EventRequest) {
  const res = await api.put<ApiResponse<EventDetail>>(API_ROUTES.events.detail(eventId), data);
  return res.data;
}

/** Removes the event; its relations stay and lose the link. ADMIN or OWNER only. */
export async function deleteEvent(eventId: string): Promise<void> {
  await api.delete(API_ROUTES.events.detail(eventId));
}
