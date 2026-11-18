"use client";

import { useCallback, useEffect, useRef } from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Toaster } from "@oba/ui/components/sonner";
import { useTicketNavigation } from "@/features/tickets/hooks/use-ticket-navigation";
import type { AllTicketListResponse } from "@/features/all-tickets/schemas";
import { useUrgentIncidents } from "../hooks/use-urgent-incidents";
import { IncidentAlertMoreToast, IncidentAlertToast } from "./incident-alert-toast";

/** Ids of the incidents this browser has opened or dismissed. */
const SEEN_STORAGE_KEY = "kaizen.incidentAlerts.seen";

/** The alerts' own toaster, so they never queue behind ordinary toasts. */
const TOASTER_ID = "incident-alerts";
const MORE_TOAST_ID = "incident-alerts-more";
const MAX_ALERTS = 3;

/**
 * Per document, not per mount: the poll failure toast is shown once in a
 * session even when the dashboard layout remounts.
 */
let pollFailureReported = false;

function readSeen(): Set<string> {
  try {
    const raw = window.localStorage.getItem(SEEN_STORAGE_KEY);
    if (!raw) return new Set();
    const parsed: unknown = JSON.parse(raw);
    return new Set(
      Array.isArray(parsed) ? parsed.filter((id): id is string => typeof id === "string") : [],
    );
  } catch (error) {
    // Blocked or corrupt storage must not stop the alert itself: the caller
    // keeps the seen ids in memory, so a dismissal holds for this session.
    console.warn("[incident-alerts] could not read dismissed alerts; keeping them in memory", error);
    return new Set();
  }
}

function writeSeen(seen: Set<string>) {
  try {
    window.localStorage.setItem(SEEN_STORAGE_KEY, JSON.stringify([...seen]));
  } catch (error) {
    // The in-memory set still holds the dismissal for this session; only
    // its survival across reloads is lost.
    console.warn("[incident-alerts] could not store dismissed alerts; keeping them in memory", error);
  }
}

/**
 * Raises a red alert for every open P1 or P2 incident on the user's boards
 * that this browser has not seen yet, on whatever dashboard page is open.
 *
 * An alert stays until the user opens or dismisses it; either marks it seen
 * here, and it does not return. An incident that leaves the urgent list
 * (closed, or lowered below P2) takes its alert with it and is forgotten,
 * so raising it again alerts again and the stored set stays small.
 *
 * At most three alerts show at once, newest first; the rest are counted in
 * one summary alert that links to the Incidents page.
 */
export function IncidentAlertWatcher() {
  const t = useTranslations("incidents.alert");
  const { data: incidents, isError, error } = useUrgentIncidents();
  const { openTicket } = useTicketNavigation();

  const seenRef = useRef<Set<string> | null>(null);
  const shownRef = useRef<Set<string>>(new Set());
  // Hidden count at the moment the summary was dismissed: it returns only
  // when more incidents are hidden than the user already waved away.
  const moreDismissedRef = useRef(0);
  const openTicketRef = useRef(openTicket);
  const latestIncidentsRef = useRef<AllTicketListResponse[] | undefined>(undefined);

  const getSeen = useCallback((): Set<string> => {
    if (seenRef.current === null) seenRef.current = readSeen();
    return seenRef.current;
  }, []);

  // `markSeen` and `syncAlerts` call each other: a dismissal brings the
  // next unseen incident into view at once, through this ref.
  const syncRef = useRef<() => void>(() => {});

  const markSeen = useCallback(
    (ticketId: string) => {
      const seen = getSeen();
      seen.add(ticketId);
      writeSeen(seen);
      toast.dismiss(ticketId);
      shownRef.current.delete(ticketId);
      syncRef.current();
    },
    [getSeen],
  );

  const syncAlerts = useCallback(() => {
    const urgent = latestIncidentsRef.current;
    if (!urgent) return;

    const seen = getSeen();
    const urgentIds = new Set(urgent.map((ticket) => ticket.id));
    let pruned = false;
    for (const id of seen) {
      if (!urgentIds.has(id)) {
        seen.delete(id);
        pruned = true;
      }
    }
    if (pruned) writeSeen(seen);

    const unseen = urgent.filter((ticket) => !seen.has(ticket.id));
    const visible = unseen.slice(0, MAX_ALERTS);
    const visibleIds = new Set(visible.map((ticket) => ticket.id));

    for (const id of [...shownRef.current]) {
      if (!visibleIds.has(id)) {
        toast.dismiss(id);
        shownRef.current.delete(id);
      }
    }

    for (const ticket of visible) {
      toast.custom(
        () => (
          <IncidentAlertToast
            ticket={ticket}
            onOpen={() => {
              markSeen(ticket.id);
              openTicketRef.current(ticket.key, ticket);
            }}
            onDismiss={() => markSeen(ticket.id)}
          />
        ),
        { id: ticket.id, duration: Infinity, dismissible: false, toasterId: TOASTER_ID },
      );
      shownRef.current.add(ticket.id);
    }

    const hidden = unseen.length - visible.length;
    if (hidden < moreDismissedRef.current) moreDismissedRef.current = hidden;
    if (hidden > moreDismissedRef.current) {
      toast.custom(
        () => (
          <IncidentAlertMoreToast
            count={hidden}
            onDismiss={() => {
              moreDismissedRef.current = hidden;
              toast.dismiss(MORE_TOAST_ID);
            }}
          />
        ),
        { id: MORE_TOAST_ID, duration: Infinity, dismissible: false, toasterId: TOASTER_ID },
      );
    } else {
      toast.dismiss(MORE_TOAST_ID);
    }
  }, [getSeen, markSeen]);

  useEffect(() => {
    syncRef.current = syncAlerts;
    openTicketRef.current = openTicket;
  }, [syncAlerts, openTicket]);

  useEffect(() => {
    latestIncidentsRef.current = incidents;
    syncAlerts();
  }, [incidents, syncAlerts]);

  // The toaster leaves with this component; its toasts must not linger in
  // sonner's global store and reappear stale on the next mount.
  useEffect(() => {
    const shown = shownRef.current;
    return () => {
      for (const id of shown) toast.dismiss(id);
      shown.clear();
      toast.dismiss(MORE_TOAST_ID);
    };
  }, []);

  useEffect(() => {
    if (!isError) return;
    console.error("[incident-alerts] urgent incident poll failed", error);
    if (pollFailureReported) return;
    pollFailureReported = true;
    toast.error(t("pollFailed"));
  }, [isError, error, t]);

  return (
    <Toaster
      id={TOASTER_ID}
      position="top-center"
      expand
      visibleToasts={MAX_ALERTS + 1}
      containerAriaLabel={t("region")}
      style={{ "--width": "26rem" } as React.CSSProperties}
    />
  );
}
