/**
 * Query keys of the alert map. Kept out of the "use client" hook file so the
 * settings page can prefetch under the exact keys the hooks read; imported
 * from a client module, every export would become a client reference that the
 * server cannot call.
 */
export const ALERT_ROUTE_KEYS = {
  all: ["alert-routes"] as const,
  list: () => [...ALERT_ROUTE_KEYS.all, "list"] as const,
};
