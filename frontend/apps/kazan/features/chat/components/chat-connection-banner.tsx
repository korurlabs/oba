"use client";

import { useEffect, useState, useSyncExternalStore } from "react";
import { useTranslations } from "next-intl";
import { Loader2, WifiOff } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useRealtimeStatus } from "@/features/realtime/hooks/use-realtime-status";
import { RTM_SEC_002 } from "@oba/shared/realtime";

/**
 * How long a connect or reconnect may take before the banner shows. A
 * short blip (a deploy, a network hand-over) heals within it, and a banner
 * that flashes for a moment only alarms the reader.
 */
const SHOW_DELAY_MS = 3000;

function subscribeOnline(listener: () => void): () => void {
  window.addEventListener("online", listener);
  window.addEventListener("offline", listener);
  return () => {
    window.removeEventListener("online", listener);
    window.removeEventListener("offline", listener);
  };
}

/**
 * The state of the live connection above the message list: "Reconnecting…"
 * while the socket recovers, "Offline" with Retry once it gave up, and a
 * notice when the server refused the session because the reader lost access.
 * The list keeps working over REST meanwhile, and the data hooks re-sync
 * when the socket is back.
 */
export function ChatConnectionBanner() {
  const t = useTranslations("chat.connection");
  const { status, errorCode, retry } = useRealtimeStatus();
  const browserOnline = useSyncExternalStore(
    subscribeOnline,
    () => navigator.onLine,
    () => true,
  );
  // The socket notices a lost network only when its heartbeat times out, so
  // the browser's own offline state counts at once.
  const unhealthy = status !== "connected" || !browserOnline;
  const [delayPassed, setDelayPassed] = useState<boolean>(false);

  // The delay restarts with every healthy period, so each new outage waits
  // again before the banner shows. Offline shows at once: the reader's
  // messages wait until the connection is back, and they should know why.
  useEffect(() => {
    if (!unhealthy) {
      setDelayPassed(false);
      return;
    }
    const timer = setTimeout(() => setDelayPassed(true), SHOW_DELAY_MS);
    return () => clearTimeout(timer);
  }, [unhealthy]);

  if (!unhealthy) return null;
  if (status !== "offline" && browserOnline && !delayPassed) return null;

  const refused = status === "offline" && errorCode === RTM_SEC_002;
  const text = refused
    ? t("noAccess")
    : !browserOnline
      ? t("browserOffline")
      : status === "offline"
        ? t("offline")
        : status === "connecting"
          ? t("connecting")
          : t("reconnecting");
  const busy = browserOnline && (status === "connecting" || status === "reconnecting");

  return (
    <div
      role="status"
      className="border-foreground/20 bg-card text-foreground flex items-center gap-2 border-b px-4 py-1.5 text-xs shadow-[inset_2px_0_0_var(--foreground)] lg:px-6"
    >
      {busy ? (
        <Loader2 className="h-3.5 w-3.5 shrink-0 animate-spin" aria-hidden />
      ) : (
        <WifiOff className="h-3.5 w-3.5 shrink-0" aria-hidden />
      )}
      <span className="min-w-0 flex-1">{text}</span>
      {!busy && browserOnline && (
        <Button variant="ghost" size="sm" className="h-6 px-2 text-xs" onClick={retry}>
          {t("retry")}
        </Button>
      )}
    </div>
  );
}
