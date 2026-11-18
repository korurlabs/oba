/**
 * Ticket Sheet Option Lists
 *
 * Provides select options for ticket type, priority, and status fields.
 * Each hook uses `useTranslations` internally and renders JSX labels
 * with the appropriate badge/icon components.
 */

"use client";

import { useTranslations } from "next-intl";
import { useMemo } from "react";
import { useFormContext } from "react-hook-form";
import { TicketTypeBadge } from "./ticket-type-badge";
import { TicketPriorityIcon } from "./ticket-priority-icon";
import { TicketStatusIcon } from "./ticket-status-icon";
import { TicketType, TicketTypeSchema, TicketStatusSchema } from "@/types/enums";
import type { TicketFormValues } from "../schemas";

type SelectOption = { value: string; label: React.ReactNode };

export function useTicketTypeOptions(): SelectOption[] {
  const t = useTranslations();
  return useMemo(
    () =>
      TicketTypeSchema.options.map((type) => ({
        value: type,
        label: (
          <span className="flex items-center gap-2">
            <TicketTypeBadge type={type} size="sm" />
            {t(`ticket.types.${type}`)}
          </span>
        ),
      })),
    [t],
  );
}

export function useStandardPriorityOptions(): SelectOption[] {
  const t = useTranslations();
  return useMemo(
    () =>
      (["LOWEST", "LOW", "MEDIUM", "HIGH", "HIGHEST"] as const).map(
        (priority) => ({
          value: priority,
          label: (
            <span className="flex items-center gap-2">
              <TicketPriorityIcon priority={priority} size={14} />
              {t(`ticket.priority.${priority}`)}
            </span>
          ),
        }),
      ),
    [t],
  );
}

export function useIncidentPriorityOptions(): SelectOption[] {
  const t = useTranslations();
  return useMemo(
    () =>
      (["P1", "P2", "P3"] as const).map((priority) => ({
        value: priority,
        label: (
          <span className="flex items-center gap-2">
            <TicketPriorityIcon priority={priority} size={14} />
            {t(`ticket.priority.${priority}`)}
          </span>
        ),
      })),
    [t],
  );
}

export function useStatusOptions(): SelectOption[] {
  const t = useTranslations();
  return useMemo(
    () =>
      TicketStatusSchema.options.map((status) => ({
        value: status,
        label: (
          <span className="flex items-center gap-2">
            <TicketStatusIcon status={status} />
            {t(`ticket.status.${status}`)}
          </span>
        ),
      })),
    [t],
  );
}

/**
 * Returns the correct priority options based on the current ticket type,
 * and resets priority when type changes to avoid invalid combinations.
 *
 * Must be used inside a FormProvider with TicketFormValues.
 */
export function usePriorityOptions(): SelectOption[] {
  const { watch } = useFormContext<TicketFormValues>();
  const standardPriorities = useStandardPriorityOptions();
  const incidentPriorities = useIncidentPriorityOptions();

  const watchedType = watch("type");
  return watchedType === TicketType.INCIDENT
    ? incidentPriorities
    : standardPriorities;
}
