"use client";

import { useTranslations } from "next-intl";
import {
  SelectedTicketNoAccessBadge,
  SelectedTicketSummary,
  SelectedTicketsSection,
} from "@/features/updates/components/selected-ticket-list";
import { PULL_REQUEST_RELATED_TICKETS_MAX } from "../schemas";
import type { RelatedTicketSelection } from "../hooks/use-related-ticket-selection";

interface RelatedTicketsCardProps {
  selection: RelatedTicketSelection;
  disabled: boolean;
}

/**
 * The related tickets of the share in pick order, listed as the daily update
 * form lists its tickets. Ticking or unticking a row in the ticket picker
 * adds or removes one here; the remove button does the same from the card.
 * A stored ticket the author can no longer open, or a deleted one, stays
 * listed with its stored key and title so it can be removed.
 */
export function RelatedTicketsCard({ selection, disabled }: RelatedTicketsCardProps) {
  const t = useTranslations("pullRequests.form");
  const { selectedIds, chosen, remove } = selection;

  return (
    <SelectedTicketsSection
      title={t("relatedTickets")}
      count={selectedIds.length}
      max={PULL_REQUEST_RELATED_TICKETS_MAX}
      tooManyMessage={t("relatedTicketsTooMany")}
      emptyMessage={t("relatedTicketsEmpty")}
      isEmpty={chosen.length === 0}
    >
      {chosen.map((ticket) => (
        <li key={ticket.ticketId} className="space-y-2 px-4 py-3">
          <SelectedTicketSummary
            ticket={ticket}
            removeLabel={t("relatedTicketRemove", { key: ticket.ticketKey })}
            onRemove={() => remove(ticket.ticketId)}
            disabled={disabled}
          />
          {!ticket.canOpen && <SelectedTicketNoAccessBadge hint={t("relatedTicketNoAccessHint")} />}
        </li>
      ))}
    </SelectedTicketsSection>
  );
}
