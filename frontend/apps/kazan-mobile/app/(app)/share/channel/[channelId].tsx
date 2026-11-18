import { useLocalSearchParams } from "expo-router";
import * as React from "react";

import { ChatChannelScreen } from "@/components/chat/ChatChannelScreen";
import { chatSharedTicketFromParams, type ChatSharedTicket } from "@/components/chat/chatShare";
import { EmptyState } from "@/components/kazan/EmptyState";
import { useTranslation } from "@/i18n";

/**
 * A channel opened to share a ticket into it, above the tabs, so Back
 * returns to the ticket. The composer starts with the ticket's chip and
 * nothing is sent until the reader taps Send.
 */
export default function ShareChannelScreen() {
  const { t } = useTranslation();
  const params = useLocalSearchParams<{ ticketId?: string; ticketKey?: string }>();
  const ticket: ChatSharedTicket | null = React.useMemo(
    () => chatSharedTicketFromParams({ ticketId: params.ticketId, ticketKey: params.ticketKey }),
    [params.ticketId, params.ticketKey]
  );
  if (!ticket) {
    return <EmptyState variant="error" title={t("chatShareInvalid")} className="mt-8" />;
  }
  return <ChatChannelScreen share={ticket} />;
}
