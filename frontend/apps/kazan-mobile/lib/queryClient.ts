import { focusManager, onlineManager, QueryClient } from "@tanstack/react-query";
import { AppState, type AppStateStatus } from "react-native";
import NetInfo from "@react-native-community/netinfo";

import { shouldRetryQuery } from "@/lib/queryRetry";

/**
 * The app's TanStack Query client.
 *
 * Mutations are not retried: a repeated write can post a chat message or move
 * a ticket twice, and a repeated sign-in would reuse a spent authorization
 * code. Queries are retried, except after a 401, 403 or 404, which a second
 * attempt cannot change (`shouldRetryQuery`).
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: shouldRetryQuery },
    mutations: { retry: 0 },
  },
});

let installed = false;

/**
 * Drives TanStack Query's focus and online managers from React Native's
 * AppState and NetInfo. Without it, `refetchOnWindowFocus` and
 * `refetchOnReconnect` do nothing on a device, because there is no window
 * focus event and no `navigator.onLine`. Idempotent; the returned function
 * lets a remount (fast refresh) install again.
 */
export function installNativeQueryManagers(): () => void {
  if (installed) return () => undefined;
  installed = true;

  focusManager.setEventListener((handleFocus) => {
    const subscription = AppState.addEventListener("change", (state: AppStateStatus) => {
      handleFocus(state === "active");
    });
    return () => subscription.remove();
  });

  onlineManager.setEventListener((setOnline) =>
    NetInfo.addEventListener((state) => {
      setOnline(state.isConnected ?? true);
    })
  );

  return () => {
    installed = false;
  };
}
