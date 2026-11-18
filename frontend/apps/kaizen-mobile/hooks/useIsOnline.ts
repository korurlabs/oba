import { onlineManager } from "@tanstack/react-query";
import * as React from "react";

function subscribe(onChange: () => void): () => void {
  return onlineManager.subscribe(onChange);
}

function getSnapshot(): boolean {
  return onlineManager.isOnline();
}

/**
 * Whether the device has a network, as the query client sees it (NetInfo
 * feeds `onlineManager`). Reading the same source as the queries keeps a
 * screen's offline state in step with when its queries pause and refetch.
 */
export function useIsOnline(): boolean {
  return React.useSyncExternalStore(subscribe, getSnapshot);
}
