import { useRouter } from "expo-router";
import * as React from "react";
import { Platform } from "react-native";

import { IncidentBanner } from "@/components/kazan/IncidentBanner";
import { useOpenP1Incidents } from "@/hooks/queries/boards";
import { useDates } from "@/hooks/useDates";
import { useTranslation } from "@/i18n";
import { hapticNotification } from "@/lib/haptics";

/**
 * The newest open P1 the reader has already felt. Home and Boards both show the
 * banner, so the error haptic belongs to the incident, not to each mount: it
 * fires once when a new incident takes the banner, for the life of the app.
 */
let lastFeltIncidentId: string | null = null;

type OpenP1IncidentBannerProps = {
  className?: string;
};

/**
 * The red banner of the newest open P1 incident at the top of Home and the
 * Boards list; it opens Incidents. iOS counts the open P1 incidents and names
 * the newest; Android names the newest and how long it has been open. It
 * renders nothing while none is open or the list has not loaded. A failed
 * read shows no banner either: the banner only points to the Incidents tab,
 * whose list shows the failure with Try again.
 */
export function OpenP1IncidentBanner({ className }: OpenP1IncidentBannerProps) {
  const { t } = useTranslation();
  const router = useRouter();
  const dates = useDates();
  const incidents = useOpenP1Incidents();
  const first = incidents.data?.[0];
  const firstId: string | undefined = first?.id;

  React.useEffect(() => {
    if (!firstId || firstId === lastFeltIncidentId) return;
    lastFeltIncidentId = firstId;
    hapticNotification("error");
  }, [firstId]);

  if (!first) return null;

  const openIncidents = (): void => router.navigate("/incidents");
  if (Platform.OS === "ios") {
    return (
      <IncidentBanner
        count={incidents.data?.length}
        message={`${first.key} ${first.title}`}
        onPress={openIncidents}
        className={className}
      />
    );
  }
  return (
    <IncidentBanner
      title={`${first.key} ${first.title}`}
      message={t("incidentsBannerMessage", {
        priority: t("ticketPriorityP1"),
        duration: dates.openDuration(first.createdAt) ?? "",
      })}
      onPress={openIncidents}
      className={className}
    />
  );
}
