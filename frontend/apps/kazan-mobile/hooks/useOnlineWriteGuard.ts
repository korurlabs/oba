import { onlineManager } from "@tanstack/react-query";
import * as React from "react";

import { toast } from "@/components/ui/toast";
import { useTranslation } from "@/i18n";

/**
 * A check to run before a write: true when the device is online, otherwise it
 * toasts that writes wait for the connection and returns false. Without it an
 * offline mutation pauses with a spinning button and sends once the device is
 * back, long after the user has moved on.
 */
export function useOnlineWriteGuard(): () => boolean {
  const { t } = useTranslation();
  return React.useCallback((): boolean => {
    if (onlineManager.isOnline()) return true;
    toast.error(t("offlineWriteBlocked"));
    return false;
  }, [t]);
}
