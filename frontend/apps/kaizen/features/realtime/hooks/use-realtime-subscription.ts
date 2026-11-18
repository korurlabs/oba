"use client";

import { useEffect, useRef } from "react";

import { useRealtimeContext, type RealtimeFrameHandler } from "../realtime-provider";

/**
 * Run `handler` for every frame of `destination` for as long as the component
 * is mounted, across reconnects. `null` pauses the subscription, for a
 * destination that cannot be named yet (no organization while the session
 * loads). A new destination ends the subscription of the previous one before
 * it subscribes, so a view that follows one item at a time (the open chat
 * channel) never holds two of them.
 *
 * The body arrives parsed but unvalidated: the handler validates it against
 * the signal schema it expects. The handler may change between renders without
 * resubscribing. Signals sent while the subscription was not in place are
 * lost; pair it with `useRealtimeResync` to refetch after a gap.
 */
export function useRealtimeSubscription(
  destination: string | null,
  handler: RealtimeFrameHandler,
): void {
  const { subscribe } = useRealtimeContext();
  const handlerRef = useRef<RealtimeFrameHandler>(handler);
  useEffect(() => {
    handlerRef.current = handler;
  });

  useEffect(() => {
    if (!destination) return;
    return subscribe(destination, (body: unknown): void => {
      handlerRef.current(body);
    });
  }, [destination, subscribe]);
}
