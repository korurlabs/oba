"use client";

import { WifiOff } from "lucide-react";

import { useKayaStream } from "../hooks/use-kaya-stream";

/**
 * Keeps the organization's live stream open on every page and says so when it
 * is down: the sessions then reload every ten seconds until it is back.
 */
export function LiveUpdates() {
  const state = useKayaStream();
  if (state !== "paused") return null;
  return (
    <p
      role="status"
      className="border-warning/50 bg-warning-light text-warning-text mb-6 flex items-start gap-2 rounded-lg border px-3 py-2 text-sm"
    >
      <WifiOff className="mt-0.5 size-4 shrink-0" aria-hidden />
      Live updates paused. Sessions reload every 10 seconds until the connection to Kaya is back.
    </p>
  );
}
