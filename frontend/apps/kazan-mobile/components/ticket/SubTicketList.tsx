import { Circle, CircleCheck, Plus } from "lucide-react-native";
import { Pressable, View } from "react-native";

import { resolveStatus } from "@/components/kazan/ticketMetaModel";
import { orderSubTickets, subTicketProgress } from "@/components/ticket/ticketModel";
import { Card } from "@/components/ui/card";
import { Icon } from "@/components/ui/icon";
import { IconButton } from "@/components/ui/icon-button";
import { ProgressBar } from "@/components/ui/progress";
import { Text } from "@/components/ui/text";
import { useTranslation } from "@/i18n";
import type { SubTicket } from "@/lib/schemas/tickets";

type SubTicketListProps = {
  subTickets: SubTicket[] | null | undefined;
  onOpenTicket: (ticketKey: string) => void;
  /** Opens New ticket as a sub-ticket of this one; without it there is no add button. */
  onAddSubTicket?: () => void;
};

/**
 * The sub-tickets with their progress: open ones first, then done ones.
 * Sub-tickets are tickets of their own, so a row opens its own ticket screen.
 * Without sub-tickets and without a way to add one, the section is left out.
 */
export function SubTicketList({ subTickets, onOpenTicket, onAddSubTicket }: SubTicketListProps) {
  const { t } = useTranslation();
  const { done, total, percent } = subTicketProgress(subTickets);
  if (total === 0 && !onAddSubTicket) return null;

  return (
    <Card className="mx-4">
      <View className="flex-row items-center gap-2">
        <Text variant="headline" className="flex-1">
          {t("ticketSubTickets")}
        </Text>
        {total > 0 ? (
          <Text variant="footnote" className="text-muted-foreground">
            {t("ticketSubTicketProgress", { done, total })}
          </Text>
        ) : null}
        {onAddSubTicket ? (
          <IconButton icon={Plus} label={t("ticketAddSubTicket")} onPress={onAddSubTicket} />
        ) : null}
      </View>
      {total > 0 ? (
        <ProgressBar label={t("ticketSubTicketProgressLabel")} value={percent} tone="success" />
      ) : null}
      {orderSubTickets(subTickets).map((ticket) => {
        const isDone: boolean = ticket.status === "DONE";
        return (
          <Pressable
            key={ticket.id}
            accessibilityRole="button"
            accessibilityLabel={[
              ticket.key,
              ticket.title,
              t(resolveStatus(ticket.status).labelKey),
            ].join(", ")}
            onPress={() => onOpenTicket(ticket.key)}
            className="min-h-11 flex-row items-center gap-3 py-1.5 active:opacity-70"
          >
            <Icon
              as={isDone ? CircleCheck : Circle}
              size={20}
              className={isDone ? "text-success" : "text-muted-foreground"}
            />
            <View className="flex-1">
              <Text
                variant="body"
                numberOfLines={2}
                className={isDone ? "text-muted-foreground line-through" : undefined}
              >
                {ticket.title}
              </Text>
              <Text variant="ticket-key">{ticket.key}</Text>
            </View>
          </Pressable>
        );
      })}
    </Card>
  );
}
