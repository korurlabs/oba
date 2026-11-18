import * as React from "react";
import { AppState, type AppStateStatus } from "react-native";

import { getPushPermission, type PushPermission } from "@/lib/push";

function messageOf(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

export interface PushPermissionState {
  /** Null until the first read answers. */
  permission: PushPermission | null;
  /** The last read failed; the row shows that the state is unknown. */
  failed: boolean;
  /** Reads the permission again, after the system dialog. */
  refresh: () => Promise<PushPermission | null>;
}

/**
 * The notification permission, read on mount and again whenever the app
 * returns to the foreground, because the user changes it in the system
 * settings, outside the app.
 */
export function usePushPermission(): PushPermissionState {
  const [permission, setPermission] = React.useState<PushPermission | null>(null);
  const [failed, setFailed] = React.useState<boolean>(false);
  const mounted = React.useRef<boolean>(true);

  const refresh = React.useCallback(async (): Promise<PushPermission | null> => {
    try {
      const next: PushPermission = await getPushPermission();
      if (mounted.current) {
        setPermission(next);
        setFailed(false);
      }
      return next;
    } catch (error: unknown) {
      console.warn("[push] reading the notification permission failed", messageOf(error));
      if (mounted.current) setFailed(true);
      return null;
    }
  }, []);

  React.useEffect(() => {
    mounted.current = true;
    void refresh();
    const subscription = AppState.addEventListener("change", (state: AppStateStatus) => {
      if (state === "active") void refresh();
    });
    return () => {
      mounted.current = false;
      subscription.remove();
    };
  }, [refresh]);

  return { permission, failed, refresh };
}
