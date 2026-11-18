import { releaseHref } from "@/features/scope/components/scope-data";

import type { SessionDetailResponse, ConfigDeliveredBy, ConfigReleaseState } from "../schemas";

export type BadgeVariant = "success" | "info" | "warning" | "error" | "muted";

/** How the session header shows the config release the session ran under. */
export type ConfigReleaseView = {
  /** "Config release 7", or what stands in for a release the session did not have. */
  label: string;
  /** The release's delivered paths; null when the session names no known release. */
  href: string | null;
  state: { text: string; variant: BadgeVariant; description: string } | null;
  /** "Delivered by the launcher" or "by the hook"; null when not reported. */
  deliveredBy: string | null;
};

const STATES: Readonly<
  Record<ConfigReleaseState, { text: string; variant: BadgeVariant; description: string }>
> = {
  FRESH: {
    text: "Fresh",
    variant: "success",
    description: "The session started on the newest release, fetched from Kaya.",
  },
  CACHED: {
    text: "Cached",
    variant: "info",
    description:
      "The session started on the release cached on the machine, because Kaya could not be reached.",
  },
  STALE: {
    text: "Stale",
    variant: "warning",
    description: "A newer release was waiting for a restart of the session.",
  },
  NONE: {
    text: "None",
    variant: "muted",
    description: "The session started without a config release.",
  },
  UNVERIFIED: {
    text: "Unverified",
    variant: "error",
    description:
      "The manifest hash the session reported matches no release of this repository, so Kaya cannot say what configuration it ran with.",
  },
};

const DELIVERED_BY: Readonly<Record<ConfigDeliveredBy, string>> = {
  LAUNCHER: "Delivered by the launcher",
  HOOK: "Delivered by the SessionStart hook",
};

/**
 * The release badge of a session. A session recorded before delivery reports no
 * state at all, which differs from a session that reported running without a
 * release (NONE).
 */
export function configReleaseView(
  detail: Pick<
    SessionDetailResponse,
    "repositoryId" | "releaseNumber" | "releaseState" | "deliveredBy"
  >
): ConfigReleaseView {
  const state = detail.releaseState ?? null;
  const number = detail.releaseNumber ?? null;
  const deliveredBy = detail.deliveredBy ? DELIVERED_BY[detail.deliveredBy] : null;
  if (state === null) {
    return { label: "No config release reported", href: null, state: null, deliveredBy };
  }
  const known = number !== null && state !== "NONE" && state !== "UNVERIFIED";
  const label = known
    ? `Config release ${number}`
    : state === "NONE"
      ? "No config release"
      : "Unknown config release";
  const href = known && detail.repositoryId ? releaseHref(detail.repositoryId, number) : null;
  return { label, href, state: STATES[state], deliveredBy };
}
