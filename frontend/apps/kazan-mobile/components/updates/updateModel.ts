import type { TranslationKey } from "@/i18n";
import { addDaysToDateOnly, parseDateOnly, toDateOnly } from "@/lib/dates";
import type { ChatMessageResponse } from "@/lib/schemas/chat";
import {
  UPDATE_ERROR_CODES,
  UPDATE_NOTE_MAX,
  UPDATE_PERCENTAGE_MAX,
  UPDATE_PERCENTAGE_MIN,
  UPDATE_TEXT_MAX,
  UPDATE_TICKETS_MAX,
  updateResponseSchema,
  type UpdateAuthorResponse,
  type UpdateChannelFilter,
  type UpdateMissingMember,
  type UpdateMissingResponse,
  type UpdateResponse,
  type UpdateTicketOption,
  type UpdateWriteRequest,
} from "@/lib/schemas/updates";

/*
 * The rules of the daily updates channel, the update form and Not posted
 * without React and without the network, so they can be tested alone. A day
 * is a `YYYY-MM-DD` business day; "today" is passed in, so a test fixes it.
 */

// ── Filters ─────────────────────────────────────────────────────────

/**
 * The stored filter of the channel. A null day follows today, so a reader
 * who left the channel on today finds it on the new today tomorrow.
 */
export interface StoredUpdateFilter {
  day: string | null;
  personId: string | null;
}

/** The filter that narrows to today and to nobody. */
export const NO_UPDATE_FILTER: StoredUpdateFilter = { day: null, personId: null };

/** The filter the channel reads, with the day resolved; a day after today falls back to today. */
export function resolveUpdateFilter(
  stored: StoredUpdateFilter,
  today: string
): UpdateChannelFilter {
  const day: string =
    stored.day && parseDateOnly(stored.day) && stored.day <= today ? stored.day : today;
  return { day, personId: stored.personId };
}

/** The stored form of a picked day: today is kept as null. */
export function storedDay(day: string, today: string): string | null {
  return day >= today ? null : day;
}

/** The number of choices that narrow the channel past its default: the badge on the filter button. */
export function updateFilterCount(filter: UpdateChannelFilter, today: string): number {
  return (filter.day !== today ? 1 : 0) + (filter.personId ? 1 : 0);
}

/** True when the reader may see Not posted and nudge: the same roles the server lets read it. */
export function canSeeNotPosted(role: string | null): boolean {
  return role === "OWNER" || role === "ADMIN";
}

// ── Cards ───────────────────────────────────────────────────────────

/**
 * The update a chat entry carries, checked against the update schema. Null
 * for a plain message, and for an entry whose update does not match, which
 * is logged and then shown as the chat's one-line entry.
 */
export function readChatDailyUpdate(
  message: Pick<ChatMessageResponse, "id" | "dailyUpdateId" | "dailyUpdate" | "deleted">
): UpdateResponse | null {
  if (message.deleted || message.dailyUpdateId == null || !message.dailyUpdate) return null;
  const parsed = updateResponseSchema.safeParse(message.dailyUpdate);
  if (parsed.success) return parsed.data;
  console.error(`[updates] unreadable update on message ${message.id}`, parsed.error.message);
  return null;
}

/** Edit and Delete, for the author only. */
export function canManageUpdate(
  update: Pick<UpdateResponse, "author">,
  meId: string | undefined
): boolean {
  return !!meId && update.author.userId === meId;
}

/** The author's first and last name, else the email, else `fallback`. */
export function updateAuthorName(author: UpdateAuthorResponse, fallback: string): string {
  const name: string = [author.firstName, author.lastName].filter(Boolean).join(" ").trim();
  return name || author.email || fallback;
}

/** Each non-blank line of a text, trimmed; one bullet each, as the copied text has them. */
export function updateTextLines(text: string | null | undefined): string[] {
  if (!text) return [];
  return text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
}

// ── Calendar ────────────────────────────────────────────────────────

/** One cell of the month grid; `day` is null for the blanks before the 1st and after the last day. */
export interface CalendarCell {
  day: string | null;
  /** A day after today, which has no updates and cannot be picked. */
  disabled: boolean;
}

/** The first day of the month `day` is in. */
export function monthOf(day: string): string {
  const date: Date | null = parseDateOnly(day);
  if (!date) return day;
  return toDateOnly(new Date(date.getFullYear(), date.getMonth(), 1));
}

/** The first day of the month `count` months after `month` (before, when negative). */
export function shiftMonth(month: string, count: number): string {
  const date: Date | null = parseDateOnly(month);
  if (!date) return month;
  return toDateOnly(new Date(date.getFullYear(), date.getMonth() + count, 1));
}

/**
 * The weeks of a month, Monday first, as the team's week starts. Days after
 * `today` are disabled: the channel has nothing there yet.
 */
export function calendarWeeks(month: string, today: string): CalendarCell[][] {
  const first: Date | null = parseDateOnly(month);
  if (!first) return [];
  const start: string = toDateOnly(new Date(first.getFullYear(), first.getMonth(), 1));
  const daysInMonth: number = new Date(first.getFullYear(), first.getMonth() + 1, 0).getDate();
  // getDay() counts from Sunday; the grid counts from Monday.
  const leading: number = (new Date(first.getFullYear(), first.getMonth(), 1).getDay() + 6) % 7;
  const cells: CalendarCell[] = [];
  for (let index = 0; index < leading; index += 1) cells.push({ day: null, disabled: true });
  for (let offset = 0; offset < daysInMonth; offset += 1) {
    const day: string = addDaysToDateOnly(start, offset);
    cells.push({ day, disabled: day > today });
  }
  while (cells.length % 7 !== 0) cells.push({ day: null, disabled: true });
  const weeks: CalendarCell[][] = [];
  for (let index = 0; index < cells.length; index += 7) weeks.push(cells.slice(index, index + 7));
  return weeks;
}

// ── Form ────────────────────────────────────────────────────────────

/** One ticket line of the form. The percentage is the field's text until it is sent. */
export interface UpdateDraftLine {
  ticketId: string;
  ticketKey: string;
  ticketTitle: string;
  boardName: string;
  percentage: string;
  note: string;
  /** The ticket's own progress when the line was added; null when unknown. */
  ticketPercentage: number | null;
}

export interface UpdateDraft {
  lines: UpdateDraftLine[];
  focus: string;
  questions: string;
}

export const EMPTY_UPDATE_DRAFT: UpdateDraft = { lines: [], focus: "", questions: "" };

function clampPercentage(value: number): number {
  return Math.min(UPDATE_PERCENTAGE_MAX, Math.max(UPDATE_PERCENTAGE_MIN, Math.round(value)));
}

/**
 * A picked ticket's line starts at the ticket's own progress: the server
 * writes every line's percentage to its ticket, so any other start would
 * change the ticket's progress without the author choosing to.
 */
export function lineFromTicketOption(option: UpdateTicketOption): UpdateDraftLine {
  const percentage: number = clampPercentage(option.percentage);
  return {
    ticketId: option.id,
    ticketKey: option.key,
    ticketTitle: option.title,
    boardName: option.board?.name ?? "",
    percentage: String(percentage),
    note: "",
    ticketPercentage: percentage,
  };
}

/**
 * The form of a posted update, for the author's edit. A line whose ticket
 * was deleted cannot be sent again, so it is left out and saving drops it.
 */
export function draftFromUpdate(update: UpdateResponse): UpdateDraft {
  const lines: UpdateDraftLine[] = update.items.flatMap((item) =>
    item.ticketId
      ? [
          {
            ticketId: item.ticketId,
            ticketKey: item.ticketKey,
            ticketTitle: item.ticketTitle,
            boardName: item.boardName,
            percentage: String(item.percentage),
            note: item.note ?? "",
            ticketPercentage: null,
          },
        ]
      : []
  );
  return { lines, focus: update.focus ?? "", questions: update.questions ?? "" };
}

/**
 * Adds the tickets not on the form yet, in the given order, up to the
 * update's limit. `added` counts the ones that fit.
 */
export function addDraftLines(
  draft: UpdateDraft,
  options: readonly UpdateTicketOption[]
): { draft: UpdateDraft; added: number } {
  const present = new Set<string>(draft.lines.map((line) => line.ticketId));
  const lines: UpdateDraftLine[] = [...draft.lines];
  let added = 0;
  for (const option of options) {
    if (lines.length >= UPDATE_TICKETS_MAX) break;
    if (present.has(option.id)) continue;
    present.add(option.id);
    lines.push(lineFromTicketOption(option));
    added += 1;
  }
  return { draft: { ...draft, lines }, added };
}

/** The tickets among `options` that the form does not hold yet. */
export function ticketsNotOnDraft(
  draft: UpdateDraft,
  options: readonly UpdateTicketOption[]
): UpdateTicketOption[] {
  const present = new Set<string>(draft.lines.map((line) => line.ticketId));
  return options.filter((option) => !present.has(option.id));
}

/** A whole number from 0 to 100 typed in a field; null for anything else. */
export function parsePercentage(text: string): number | null {
  const trimmed: string = text.trim();
  if (!/^\d{1,3}$/.test(trimmed)) return null;
  const value: number = Number(trimmed);
  return value >= UPDATE_PERCENTAGE_MIN && value <= UPDATE_PERCENTAGE_MAX ? value : null;
}

/** True when saving the line also sets its ticket's progress, which the form says. */
export function lineSetsTicketProgress(line: UpdateDraftLine): boolean {
  const value: number | null = parsePercentage(line.percentage);
  return value !== null && line.ticketPercentage !== null && value !== line.ticketPercentage;
}

/** The fields of the update form that stop Post, with the key of the message under each. */
export interface UpdateDraftProblems {
  tickets?: TranslationKey;
  lines: Record<string, { percentage?: TranslationKey; note?: TranslationKey }>;
  focus?: TranslationKey;
  questions?: TranslationKey;
}

/** The same checks the server makes, so Post stays off until the form would pass. */
export function updateDraftProblems(draft: UpdateDraft): UpdateDraftProblems {
  const problems: UpdateDraftProblems = { lines: {} };
  if (draft.lines.length === 0) problems.tickets = "updFormTicketsRequired";
  if (draft.lines.length > UPDATE_TICKETS_MAX) problems.tickets = "updFormTicketsTooMany";
  for (const line of draft.lines) {
    const lineProblems: { percentage?: TranslationKey; note?: TranslationKey } = {};
    if (parsePercentage(line.percentage) === null)
      lineProblems.percentage = "updFormPercentageRange";
    if (line.note.length > UPDATE_NOTE_MAX) lineProblems.note = "updFormNoteTooLong";
    if (lineProblems.percentage || lineProblems.note) problems.lines[line.ticketId] = lineProblems;
  }
  if (draft.focus.length > UPDATE_TEXT_MAX) problems.focus = "updFormTextTooLong";
  if (draft.questions.length > UPDATE_TEXT_MAX) problems.questions = "updFormTextTooLong";
  return problems;
}

export function hasUpdateDraftProblems(problems: UpdateDraftProblems): boolean {
  return (
    !!problems.tickets ||
    !!problems.focus ||
    !!problems.questions ||
    Object.keys(problems.lines).length > 0
  );
}

/** Blank texts are sent as null; the server renders them as "None". */
function textOrNull(text: string): string | null {
  return text.trim() ? text : null;
}

/** The create and edit body, lines in form order. */
export function toUpdateWriteRequest(draft: UpdateDraft): UpdateWriteRequest {
  return {
    tickets: draft.lines.map((line) => {
      const percentage: number | null = parsePercentage(line.percentage);
      if (percentage === null) {
        throw new Error(
          `A daily update line was submitted without a valid percentage: ${line.ticketKey}`
        );
      }
      return { ticketId: line.ticketId, percentage, note: textOrNull(line.note) };
    }),
    focus: textOrNull(draft.focus),
    questions: textOrNull(draft.questions),
  };
}

// ── Not posted ──────────────────────────────────────────────────────

/** The report split into those with nothing that day and those who posted after the deadline. */
export function splitMissingMembers(report: Pick<UpdateMissingResponse, "members">): {
  notPosted: UpdateMissingMember[];
  late: UpdateMissingMember[];
} {
  return {
    notPosted: report.members.filter((member) => member.postedAt === null),
    late: report.members.filter((member) => member.postedAt !== null),
  };
}

/** How many expected members had posted by the deadline. */
export function postedOnTimeCount(
  report: Pick<UpdateMissingResponse, "expectedCount" | "members">
): number {
  return Math.max(0, report.expectedCount - report.members.length);
}

/** How many expected members have posted that day, late ones included. */
export function postedCount(
  report: Pick<UpdateMissingResponse, "expectedCount" | "members">
): number {
  return Math.max(0, report.expectedCount - splitMissingMembers(report).notPosted.length);
}

/** Whole minutes from `now` until the deadline; null once it has passed or when it is unreadable. */
export function minutesUntilDeadline(deadline: string, now: Date): number | null {
  const at: number = Date.parse(deadline);
  if (Number.isNaN(at)) return null;
  const minutes: number = Math.ceil((at - now.getTime()) / 60_000);
  return minutes > 0 ? minutes : null;
}

/** A missing member's name, else the email. */
export function missingMemberName(member: UpdateMissingMember): string {
  const name: string = [member.firstName, member.lastName].filter(Boolean).join(" ").trim();
  return name || member.email;
}

/** What a refused nudge means for the row and which message the sheet shows. */
export interface NudgeRefusal {
  messageKey: TranslationKey;
  /** The member counts as nudged today: the row shows Nudged and no button. */
  markNudged: boolean;
  /** The member's state changed on the server: Not posted is read again. */
  refresh: boolean;
}

/** The meaning of a nudge refusal by the server's error code; a generic failure for any other. */
export function nudgeRefusal(code: string | undefined): NudgeRefusal {
  switch (code) {
    case UPDATE_ERROR_CODES.nudgeAlreadySent:
      return { messageKey: "updNudgeAlreadySent", markNudged: true, refresh: false };
    case UPDATE_ERROR_CODES.nudgeRecipientPosted:
      return { messageKey: "updNudgeRecipientPosted", markNudged: false, refresh: true };
    case UPDATE_ERROR_CODES.nudgeRecipientNotFound:
      return { messageKey: "updNudgeRecipientNotFound", markNudged: false, refresh: true };
    case UPDATE_ERROR_CODES.nudgeSelf:
      return { messageKey: "updNudgeSelf", markNudged: false, refresh: false };
    case UPDATE_ERROR_CODES.roleTooLow:
      return { messageKey: "updNudgeNotAllowed", markNudged: false, refresh: false };
    default:
      return { messageKey: "updNudgeFailed", markNudged: false, refresh: false };
  }
}
