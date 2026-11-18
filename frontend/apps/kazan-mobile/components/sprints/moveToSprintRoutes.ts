import { normalizeTicketKey } from "@/components/ticket/ticketModel";

/*
 * The app paths of the Move to sprint sheet, without React. The sheet opens
 * over a board view in the Boards tab and over the ticket screen above the
 * tabs, so it has a route in each stack; both read the same parameters.
 */

/** What Move to sprint opens on; every field is optional. */
export interface MoveToSprintPreset {
  /** The sprint, or `BACKLOG_PLACEMENT`, the target starts on. */
  sprintId?: string | null;
  /** Opens on "This ticket and the sub-tickets I pick". */
  pickSubTickets?: boolean;
}

function presetParams(preset: MoveToSprintPreset): Record<string, string> {
  return {
    ...(preset.sprintId ? { sprintId: preset.sprintId } : {}),
    ...(preset.pickSubTickets ? { subTickets: "pick" } : {}),
  };
}

/** Move to sprint over a board view, for the ticket of `ticketKey`. */
export function boardMoveToSprintHref(
  boardId: string,
  ticketKey: string,
  preset: MoveToSprintPreset = {}
) {
  return {
    pathname: "/boards/board/[boardId]/move-to-sprint" as const,
    params: { boardId, ticketKey, ...presetParams(preset) },
  };
}

/** Move to sprint over the ticket screen. */
export function ticketMoveToSprintHref(ticketKey: string, preset: MoveToSprintPreset = {}) {
  return {
    pathname: "/ticket/[ticketKey]/move-to-sprint" as const,
    params: { ticketKey, ...presetParams(preset) },
  };
}

/** The ticket and preset of Move to sprint, read back from its route parameters. */
export function parseMoveToSprintParams(params: Record<string, string | string[] | undefined>): {
  ticketKey: string | null;
  sprintId: string | null;
  pickSubTickets: boolean;
} {
  const read = (name: string): string | null => {
    const raw: string | string[] | undefined = params[name];
    const value: string | undefined = Array.isArray(raw) ? raw[0] : raw;
    return value ? value : null;
  };
  return {
    ticketKey: normalizeTicketKey(params.ticketKey),
    sprintId: read("sprintId"),
    pickSubTickets: read("subTickets") === "pick",
  };
}
