"use client";

import type { RealtimeStatus } from "@oba/shared/realtime";
import { useRealtimeContext } from "../realtime-provider";

export interface RealtimeStatusValue {
  status: RealtimeStatus;
  /** The ERROR code behind `reconnecting` or `offline` (`RTM_SEC_001`, `RTM_SEC_002`), or null. */
  errorCode: string | null;
  /** Connect at once: skips a backoff wait, or leaves `offline` after a refusal. */
  retry: () => void;
}

/**
 * The connection state, for a connection banner and for effects that depend
 * on it. Re-syncing data after a gap in the signals is `useRealtimeResync`.
 */
export function useRealtimeStatus(): RealtimeStatusValue {
  const { status, errorCode, retry } = useRealtimeContext();
  return { status, errorCode, retry };
}
