"use client";

import { useTranslations } from "next-intl";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Checkbox } from "@/components/ui/checkbox";
import { cn } from "@oba/ui/lib/utils";
import {
  SubTicketMoveMode,
  TicketStatus,
  type TicketListResponse,
} from "../schemas";

// ── Model ──────────────────────────────────────────────────────────

/**
 * Which sub-tickets come along when a parent moves. `subTicketIds` is set only
 * with `SELECTED`: the server refuses ids with any other mode, so the object
 * can be spread into a move request as it is.
 */
export interface SubTicketMoveChoice {
  subTicketMode: SubTicketMoveMode;
  subTicketIds?: string[];
}

/** The server's default: the unfinished sub-tickets placed with the parent come along. */
export const DEFAULT_SUB_TICKET_CHOICE: SubTicketMoveChoice = {
  subTicketMode: SubTicketMoveMode.UNFINISHED,
};

/** A descendant of the moved ticket, flattened with its depth for the checklist. */
interface SubTicketChoiceItem {
  ticket: TicketListResponse;
  depth: number;
}

function collectDescendants(
  tickets: readonly TicketListResponse[] | null | undefined,
  depth = 0,
  items: SubTicketChoiceItem[] = []
): SubTicketChoiceItem[] {
  tickets?.forEach((ticket) => {
    items.push({ ticket, depth });
    collectDescendants(ticket.subTickets, depth + 1, items);
  });
  return items;
}

export function hasSubTickets(ticket: TicketListResponse | undefined): boolean {
  return (ticket?.subTickets?.length ?? 0) > 0;
}

/**
 * Whether `descendant` is placed where `ticket` is: the same sprint, or both
 * in the backlog. A parent and its sub-tickets share a board, so two tickets
 * without a sprint are in the same backlog.
 */
function isPlacedWith(descendant: TicketListResponse, ticket: TicketListResponse): boolean {
  return (descendant.sprint?.id ?? null) === (ticket.sprint?.id ?? null);
}

/**
 * The descendants an `UNFINISHED` move takes along: not done and placed where
 * the moved ticket is. "Pick" starts with these ticked, so it starts from what
 * the default would move.
 */
export function unfinishedSubTicketIdsPlacedWith(ticket: TicketListResponse): string[] {
  return collectDescendants(ticket.subTickets)
    .filter(
      ({ ticket: descendant }) =>
        descendant.status !== TicketStatus.DONE && isPlacedWith(descendant, ticket)
    )
    .map(({ ticket: descendant }) => descendant.id);
}

// ── Component ──────────────────────────────────────────────────────

interface SubTicketChoiceProps {
  /** The ticket being moved; its `subTickets` tree is the checklist. */
  ticket?: TicketListResponse;
  value: SubTicketMoveChoice;
  onChange: (value: SubTicketMoveChoice) => void;
  /**
   * Offers "pick". Off for a bulk move, where one checklist cannot cover the
   * sub-tickets of several parents.
   */
  allowPick?: boolean;
  /** Prefix for the radio ids, unique per dialog. */
  idPrefix: string;
  disabled?: boolean;
}

/**
 * Radio group for the sub-tickets that move with a parent: only the parent,
 * the parent and its unfinished sub-tickets (the default), or a picked set
 * from a checklist of every descendant with where it is planned.
 */
export function SubTicketChoice({
  ticket,
  value,
  onChange,
  allowPick = true,
  idPrefix,
  disabled,
}: SubTicketChoiceProps) {
  const t = useTranslations("ticket.subTicketChoice");

  const descendants = ticket ? collectDescendants(ticket.subTickets) : [];
  const selectedIds = new Set(value.subTicketIds ?? []);

  const handleModeChange = (mode: string) => {
    if (mode === SubTicketMoveMode.SELECTED && ticket) {
      onChange({
        subTicketMode: SubTicketMoveMode.SELECTED,
        subTicketIds: unfinishedSubTicketIdsPlacedWith(ticket),
      });
      return;
    }
    onChange({ subTicketMode: mode as SubTicketMoveMode });
  };

  const handleToggle = (id: string, checked: boolean) => {
    const next = new Set(selectedIds);
    if (checked) next.add(id);
    else next.delete(id);
    onChange({ subTicketMode: SubTicketMoveMode.SELECTED, subTicketIds: [...next] });
  };

  const options: { mode: SubTicketMoveMode; label: string }[] = [
    { mode: SubTicketMoveMode.NONE, label: t("none") },
    { mode: SubTicketMoveMode.UNFINISHED, label: t("unfinished") },
    ...(allowPick && descendants.length > 0
      ? [{ mode: SubTicketMoveMode.SELECTED, label: t("selected") }]
      : []),
  ];

  return (
    <div className="flex flex-col gap-2">
      <span className="text-foreground text-sm font-semibold">{t("label")}</span>
      <RadioGroup value={value.subTicketMode} onValueChange={handleModeChange} disabled={disabled}>
        {options.map((option) => {
          const id = `${idPrefix}-sub-tickets-${option.mode.toLowerCase()}`;
          return (
            <label
              key={option.mode}
              htmlFor={id}
              className="border-border hover:bg-accent flex cursor-pointer items-center gap-2 rounded-lg border p-2.5 text-sm transition-colors"
            >
              <RadioGroupItem value={option.mode} id={id} />
              <span>{option.label}</span>
            </label>
          );
        })}
      </RadioGroup>

      {value.subTicketMode === SubTicketMoveMode.SELECTED && (
        <ul
          className="border-border max-h-56 overflow-y-auto rounded-lg border p-1"
          aria-label={t("checklistLabel")}
        >
          {descendants.map(({ ticket: descendant, depth }) => {
            const id = `${idPrefix}-sub-ticket-${descendant.id}`;
            const isDone = descendant.status === TicketStatus.DONE;
            return (
              <li key={descendant.id}>
                <label
                  htmlFor={id}
                  className="hover:bg-accent flex cursor-pointer items-center gap-2 rounded-md px-2 py-1.5 text-xs"
                  style={{ paddingLeft: `${0.5 + depth * 1}rem` }}
                >
                  <Checkbox
                    id={id}
                    checked={selectedIds.has(descendant.id)}
                    onCheckedChange={(checked) => handleToggle(descendant.id, checked === true)}
                    disabled={disabled}
                  />
                  <span className="text-muted-foreground shrink-0 font-mono">{descendant.key}</span>
                  <span className={cn("flex-1 truncate", isDone && "text-muted-foreground line-through")}>
                    {descendant.title}
                  </span>
                  <span className="text-muted-foreground shrink-0">
                    {isDone
                      ? t("done")
                      : descendant.sprint
                        ? descendant.sprint.name
                        : t("backlog")}
                  </span>
                </label>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
