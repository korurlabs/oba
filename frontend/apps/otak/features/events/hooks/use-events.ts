"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { useOrganizationId } from "@/hooks/use-organization-id";
import { RELATION_KEYS } from "@/features/relations/hooks/query-keys";
import { createEvent, deleteEvent, getEvent, listEvents, updateEvent } from "../api";
import { eventErrorText } from "../lib/event-error";
import { EVENT_OPTIONS_LIMIT, type EventQueryRequest, type EventRequest } from "../schemas";
import { EVENT_KEYS } from "./query-keys";

export { EVENT_KEYS };

/** One page of the organization's events; waits for the organization. */
export function useEvents(params: EventQueryRequest) {
  const organizationId = useOrganizationId();
  return useQuery({
    queryKey: EVENT_KEYS.list(organizationId ?? "", params),
    queryFn: () => listEvents(params),
    enabled: !!organizationId,
    // A new tab, search or page keeps the current rows until its answer
    // arrives, but never the rows of an organization the user just left.
    placeholderData: (previous, previousQuery) =>
      previousQuery?.queryKey[1] === organizationId ? previous : undefined,
  });
}

/** One event; waits for the organization and an id. */
export function useEvent(eventId: string, { enabled = true }: { enabled?: boolean } = {}) {
  const organizationId = useOrganizationId();
  return useQuery({
    queryKey: EVENT_KEYS.detail(organizationId ?? "", eventId),
    queryFn: () => getEvent(eventId),
    enabled: enabled && !!organizationId && eventId !== "",
  });
}

const EVENT_OPTIONS_PARAMS: EventQueryRequest = { page: 1, amount: EVENT_OPTIONS_LIMIT };

/**
 * The events a relation can be linked to: every period, the most recent
 * start first, as select options. A stored link outside them is added by the
 * form.
 */
export function useEventOptions() {
  const organizationId = useOrganizationId();
  return useQuery({
    queryKey: EVENT_KEYS.list(organizationId ?? "", EVENT_OPTIONS_PARAMS),
    queryFn: () => listEvents(EVENT_OPTIONS_PARAMS),
    enabled: !!organizationId,
    select: (response) =>
      (response.data ?? []).map((event) => ({ value: event.id, label: event.name })),
  });
}

/**
 * An event change refreshes every event query of the organization: the
 * lists, the details and the relation form's options.
 */
function useInvalidateEvents() {
  const queryClient = useQueryClient();
  const organizationId = useOrganizationId();
  return () => {
    if (organizationId) {
      void queryClient.invalidateQueries({ queryKey: EVENT_KEYS.all(organizationId) });
    }
  };
}

export function useCreateEvent() {
  const invalidate = useInvalidateEvents();
  const t = useTranslations("events");

  return useMutation({
    mutationFn: (data: EventRequest) => createEvent(data),
    onSuccess: () => {
      toast.success(t("toast.created"));
      invalidate();
    },
    onError: (error: unknown) => {
      toast.error(eventErrorText(error, t, "toast.saveError"));
    },
  });
}

export function useUpdateEvent() {
  const invalidate = useInvalidateEvents();
  const t = useTranslations("events");

  return useMutation({
    mutationFn: ({ eventId, data }: { eventId: string; data: EventRequest }) =>
      updateEvent(eventId, data),
    onSuccess: () => {
      toast.success(t("toast.updated"));
      invalidate();
    },
    onError: (error: unknown) => {
      toast.error(eventErrorText(error, t, "toast.saveError"));
    },
  });
}

/**
 * Deleting an event unlinks its relations on the server, so the relation
 * queries are refreshed too.
 */
export function useDeleteEvent() {
  const queryClient = useQueryClient();
  const organizationId = useOrganizationId();
  const t = useTranslations("events");

  return useMutation({
    mutationFn: (eventId: string) => deleteEvent(eventId),
    onSuccess: (_result, eventId) => {
      toast.success(t("toast.deleted"));
      if (organizationId) {
        queryClient.removeQueries({ queryKey: EVENT_KEYS.detail(organizationId, eventId) });
        void queryClient.invalidateQueries({ queryKey: EVENT_KEYS.all(organizationId) });
        void queryClient.invalidateQueries({ queryKey: RELATION_KEYS.all(organizationId) });
      }
    },
    onError: (error: unknown) => {
      toast.error(eventErrorText(error, t, "toast.deleteError"));
    },
  });
}
