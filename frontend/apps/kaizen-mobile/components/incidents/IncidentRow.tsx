import { incidentTiming } from "@/components/incidents/incidentModel";
import { TicketCard } from "@/components/kaizen/TicketCard";
import { useDates } from "@/hooks/useDates";
import { useTranslation } from "@/i18n";
import type { TicketListItem } from "@/lib/schemas/tickets";

type IncidentRowProps = {
  incident: TicketListItem;
  /** An open incident shows how long it has been open; a resolved one the day it opened. */
  resolved: boolean;
  onPress: () => void;
};

/**
 * An incident as the shared ticket card: its board named above the card,
 * because the tab lists every board, and the open time in place of the due
 * day.
 */
function IncidentRow({ incident, resolved, onPress }: IncidentRowProps) {
  const { t } = useTranslation();
  const dates = useDates();
  return (
    <TicketCard
      ticket={incident}
      boardName={incident.board?.name ?? null}
      timing={incidentTiming(incident, resolved, dates, t)}
      onPress={onPress}
      className="mx-4"
    />
  );
}

export { IncidentRow };
export type { IncidentRowProps };
