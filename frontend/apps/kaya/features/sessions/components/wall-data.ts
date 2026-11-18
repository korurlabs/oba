import type { BadgeState } from "@/components/common";

import type { SessionFilterSubscription, SessionListResponse } from "../schemas";

/** The state a whole session shows on its tile. */
export type SessionState = Extract<BadgeState, "running" | "done">;

/** The session's state: done once it ended, else running. */
export function sessionState(session: Pick<SessionListResponse, "ended">): SessionState {
  return session.ended ? "done" : "running";
}

/** The title to show: the session's own, else a fixed text. */
export function sessionTitle(session: { title?: string | null }): string {
  return session.title?.trim() || "Untitled session";
}

/** "Max 20x" style text of the Claude subscription a session ran on, or null when unknown. */
export function subscriptionLabel(
  session: Pick<SessionListResponse, "accountOrganizationName" | "seatTier" | "billingType">
): string | null {
  const parts = [session.accountOrganizationName, session.seatTier ?? session.billingType].filter(
    (part): part is string => typeof part === "string" && part !== ""
  );
  return parts.length === 0 ? null : parts.join(" · ");
}

/** The option text of a subscription in the wall's select. */
export function subscriptionOptionLabel(subscription: SessionFilterSubscription): string {
  const tier = subscription.seatTier ?? subscription.billingType;
  const name = subscription.name ?? subscription.uuid;
  return tier ? `${name} (${tier})` : name;
}

/** "just now", "5 min ago", "3 h ago" or "2 d ago". */
export function relativeTime(iso: string, now: number): string {
  const then = Date.parse(iso);
  if (Number.isNaN(then)) return iso;
  const seconds = Math.max(0, Math.round((now - then) / 1000));
  if (seconds < 60) return "just now";
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} h ago`;
  return `${Math.floor(hours / 24)} d ago`;
}
