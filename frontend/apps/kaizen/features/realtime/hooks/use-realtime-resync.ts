"use client";

import { useEffect, useRef } from "react";

import { useRealtimeContext } from "../realtime-provider";

/**
 * Run `onResync` after every point where signals may have been missed: each
 * reconnect, and a frame that could not be read. The point already passed at
 * mount is skipped, because the data was fetched for this mount anyway.
 *
 * `onResync` refetches what the subscriptions keep live (usually by
 * invalidating queries). It may change between renders; the latest one runs.
 */
export function useRealtimeResync(onResync: () => void): void {
  const { resyncCount } = useRealtimeContext();
  const onResyncRef = useRef<() => void>(onResync);
  useEffect(() => {
    onResyncRef.current = onResync;
  });

  const seenResyncRef = useRef<number>(resyncCount);
  useEffect(() => {
    if (resyncCount === seenResyncRef.current) return;
    seenResyncRef.current = resyncCount;
    onResyncRef.current();
  }, [resyncCount]);
}
