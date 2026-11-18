import { useLocalSearchParams } from "expo-router";

import { NewTicketScreen } from "@/components/ticket/NewTicketScreen";
import { TicketDetailScreen } from "@/components/ticket/TicketDetailScreen";

/**
 * The ticket screen, for `/ticket/[ticketKey]` and `/ticket/new`. With a
 * ticket key it shows that ticket and saves each change at once; without
 * one it is New ticket, whose same rows and sheets fill a draft until Create.
 */
export function TicketScreen() {
  const params = useLocalSearchParams<{ ticketKey?: string | string[] }>();
  return params.ticketKey === undefined ? <NewTicketScreen /> : <TicketDetailScreen />;
}
