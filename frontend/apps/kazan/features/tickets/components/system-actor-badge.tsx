"use client";

import { useTranslations } from "next-intl";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import type { TicketCreatedBySystemActorResponse } from "../schemas";

interface SystemActorBadgeProps {
  actor: TicketCreatedBySystemActorResponse;
  className?: string;
}

/**
 * Shows a ticket creator that is a system actor (an integration such as
 * Alertmanager) rather than a user, so it is never mistaken for a person.
 */
export function SystemActorBadge({ actor, className }: SystemActorBadgeProps) {
  const t = useTranslations();

  return (
    <span className={cn("inline-flex items-center gap-1 whitespace-nowrap", className)}>
      <span className="text-muted-foreground text-xs">{actor.name}</span>
      <Badge variant="secondary" className="px-1 py-0 text-[10px] font-normal">
        {t("ticket.systemActor.badge")}
      </Badge>
    </span>
  );
}
