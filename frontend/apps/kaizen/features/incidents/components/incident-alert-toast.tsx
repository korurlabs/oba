"use client";

import { Siren } from "lucide-react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Link } from "@oba/shared/libs/i18n/navigation";
import type { AllTicketListResponse } from "@/features/all-tickets/schemas";

/**
 * Red on purpose: `--destructive` is the only colour reserved for danger, so
 * an urgent incident never reads like an ordinary notification. White text
 * on it meets 4.5:1 in both themes.
 */
const ALERT_SURFACE =
  "bg-destructive text-white w-[26rem] max-w-[calc(100vw-2rem)] rounded-lg p-4 shadow-lg";

/** Buttons sit on the red surface, so focus rings are white on red. */
const ON_RED_FOCUS = "focus-visible:ring-white focus-visible:ring-offset-destructive";

/** Where the summary toast sends the user: every urgent incident at once. */
export const URGENT_INCIDENTS_HREF = "/incidents?priorities=P1,P2";

interface IncidentAlertToastProps {
  ticket: AllTicketListResponse;
  onOpen: () => void;
  onDismiss: () => void;
}

export function IncidentAlertToast({ ticket, onOpen, onDismiss }: IncidentAlertToastProps) {
  const t = useTranslations("incidents.alert");

  return (
    <div role="alert" className={ALERT_SURFACE}>
      <div className="flex items-start gap-3">
        <Siren className="mt-1 size-6 shrink-0" aria-hidden="true" />
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2 text-sm">
            <span className="rounded border border-white/70 px-1.5 py-0.5 text-xs font-bold">
              {ticket.priority}
            </span>
            <span className="truncate">
              {t("title", { priority: ticket.priority, board: ticket.board?.name ?? "" })}
            </span>
          </div>
          <p className="mt-1 text-lg leading-snug font-semibold break-words">
            <span className="mr-2 font-mono">{ticket.key}</span>
            {ticket.title}
          </p>
          <div className="mt-3 flex justify-end gap-2">
            <Button
              size="sm"
              variant="outline"
              className={`border-white/70 text-white hover:bg-white/15 hover:text-white ${ON_RED_FOCUS}`}
              onClick={onDismiss}
            >
              {t("dismiss")}
            </Button>
            <Button
              size="sm"
              variant="secondary"
              className={`text-destructive border-white hover:bg-white/90 ${ON_RED_FOCUS}`}
              onClick={onOpen}
            >
              {t("open")}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}

interface IncidentAlertMoreToastProps {
  count: number;
  onDismiss: () => void;
}

/** Stands for the urgent incidents beyond the ones shown as their own alert. */
export function IncidentAlertMoreToast({ count, onDismiss }: IncidentAlertMoreToastProps) {
  const t = useTranslations("incidents.alert");

  return (
    <div role="alert" className={ALERT_SURFACE}>
      <div className="flex items-center gap-3">
        <Siren className="size-6 shrink-0" aria-hidden="true" />
        <Link
          href={URGENT_INCIDENTS_HREF}
          className="min-w-0 flex-1 rounded text-lg font-semibold underline underline-offset-4 focus-visible:ring-2 focus-visible:ring-white focus-visible:outline-none"
        >
          {t("more", { count })}
        </Link>
        <Button
          size="sm"
          variant="outline"
          className={`border-white/70 text-white hover:bg-white/15 hover:text-white ${ON_RED_FOCUS}`}
          onClick={onDismiss}
        >
          {t("dismiss")}
        </Button>
      </div>
    </div>
  );
}
