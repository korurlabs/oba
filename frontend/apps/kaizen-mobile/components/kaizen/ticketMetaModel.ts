import type { TranslationKey } from "@/i18n";

/*
 * Ticket and milestone metadata as the kit draws it: the label key and the
 * token classes of each status, type, priority, health and mission rank. Pure,
 * so `bun test` covers the fallbacks; the components that draw them are in TicketMeta.tsx.
 *
 * The API may send a value this build does not know yet (a new status or
 * type). Each resolver then falls back to a neutral entry instead of throwing,
 * so one new enum value cannot break a whole list.
 */

export const TICKET_STATUSES = [
  "TODO",
  "IN_PROGRESS",
  "WAITING_FOR_THIRD_PARTY",
  "IN_REVIEW",
  "DONE",
] as const;
export type TicketStatus = (typeof TICKET_STATUSES)[number];

type StatusMeta = {
  labelKey: TranslationKey;
  /** The fixed status colour: the dot in rows and column headers. */
  dotClassName: string;
  /** The pill ground. */
  pillClassName: string;
  /** The kanban column ground. */
  columnClassName: string;
};

const STATUS_META: Record<TicketStatus, StatusMeta> = {
  TODO: {
    labelKey: "ticketStatusTodo",
    dotClassName: "bg-status-todo",
    pillClassName: "bg-status-bg-todo",
    columnClassName: "bg-column-todo",
  },
  IN_PROGRESS: {
    labelKey: "ticketStatusInProgress",
    dotClassName: "bg-status-in-progress",
    pillClassName: "bg-status-bg-in-progress",
    columnClassName: "bg-column-in-progress",
  },
  WAITING_FOR_THIRD_PARTY: {
    labelKey: "ticketStatusWaiting",
    dotClassName: "bg-status-waiting",
    pillClassName: "bg-status-bg-waiting",
    columnClassName: "bg-column-waiting",
  },
  IN_REVIEW: {
    labelKey: "ticketStatusInReview",
    dotClassName: "bg-status-in-review",
    pillClassName: "bg-status-bg-in-review",
    columnClassName: "bg-column-in-review",
  },
  DONE: {
    labelKey: "ticketStatusDone",
    dotClassName: "bg-status-done",
    pillClassName: "bg-status-bg-done",
    columnClassName: "bg-column-done",
  },
};

function isTicketStatus(value: string): value is TicketStatus {
  return (TICKET_STATUSES as readonly string[]).includes(value);
}

/** The status entry; an unknown status shows as Todo. */
export function resolveStatus(
  status: string | null | undefined
): StatusMeta & { status: TicketStatus } {
  const known: TicketStatus = status && isTicketStatus(status) ? status : "TODO";
  return { status: known, ...STATUS_META[known] };
}

export const TICKET_TYPES = [
  "BUG",
  "FEATURE",
  "TECH_DEBT",
  "IMPROVEMENT",
  "INCIDENT",
  "PROBLEM",
  "REFACTOR",
] as const;
export type TicketType = (typeof TICKET_TYPES)[number];

type TypeMeta = {
  labelKey: TranslationKey;
  /** The badge ground. */
  bgClassName: string;
  /** The badge text and icon colour. */
  fgClassName: string;
};

const TYPE_META: Record<TicketType, TypeMeta> = {
  BUG: {
    labelKey: "ticketTypeBug",
    bgClassName: "bg-type-bug-bg",
    fgClassName: "text-type-bug-fg",
  },
  FEATURE: {
    labelKey: "ticketTypeFeature",
    bgClassName: "bg-type-feature-bg",
    fgClassName: "text-type-feature-fg",
  },
  TECH_DEBT: {
    labelKey: "ticketTypeTechDebt",
    bgClassName: "bg-type-tech-debt-bg",
    fgClassName: "text-type-tech-debt-fg",
  },
  IMPROVEMENT: {
    labelKey: "ticketTypeImprovement",
    bgClassName: "bg-type-improvement-bg",
    fgClassName: "text-type-improvement-fg",
  },
  INCIDENT: {
    labelKey: "ticketTypeIncident",
    bgClassName: "bg-type-incident-bg",
    fgClassName: "text-type-incident-fg",
  },
  PROBLEM: {
    labelKey: "ticketTypeProblem",
    bgClassName: "bg-type-problem-bg",
    fgClassName: "text-type-problem-fg",
  },
  REFACTOR: {
    labelKey: "ticketTypeRefactor",
    bgClassName: "bg-type-refactor-bg",
    fgClassName: "text-type-refactor-fg",
  },
};

function isTicketType(value: string): value is TicketType {
  return (TICKET_TYPES as readonly string[]).includes(value);
}

/** "TECH_DEBT" becomes "Tech debt": the label of a type this build has no key for. */
export function humanizeEnum(value: string): string {
  const words = value.trim().toLowerCase().replace(/_+/g, " ");
  return words.charAt(0).toUpperCase() + words.slice(1);
}

type ResolvedType = TypeMeta & {
  type: TicketType;
  /** Set only for a type this build does not know: show it instead of the key. */
  rawLabel?: string;
};

/**
 * The type entry. The legacy TASK type and a missing type render as Feature,
 * the default type. Another unknown type keeps Feature's colours but shows its
 * own name, so the user still sees what it is.
 */
export function resolveTicketType(type: string | null | undefined): ResolvedType {
  if (type && isTicketType(type)) return { type, ...TYPE_META[type] };
  if (!type || !type.trim() || type === "TASK") return { type: "FEATURE", ...TYPE_META.FEATURE };
  return { type: "FEATURE", ...TYPE_META.FEATURE, rawLabel: humanizeEnum(type) };
}

export const TICKET_PRIORITIES = [
  "LOWEST",
  "LOW",
  "MEDIUM",
  "HIGH",
  "HIGHEST",
  "P1",
  "P2",
  "P3",
] as const;
export type TicketPriority = (typeof TICKET_PRIORITIES)[number];

type PriorityMeta = {
  labelKey: TranslationKey;
  /** The icon colour. */
  className: string;
};

const PRIORITY_META: Record<TicketPriority, PriorityMeta> = {
  LOWEST: { labelKey: "ticketPriorityLowest", className: "text-priority-lowest" },
  LOW: { labelKey: "ticketPriorityLow", className: "text-priority-low" },
  MEDIUM: { labelKey: "ticketPriorityMedium", className: "text-priority-medium" },
  HIGH: { labelKey: "ticketPriorityHigh", className: "text-priority-high" },
  HIGHEST: { labelKey: "ticketPriorityHighest", className: "text-priority-highest" },
  P1: { labelKey: "ticketPriorityP1", className: "text-priority-p1" },
  P2: { labelKey: "ticketPriorityP2", className: "text-priority-p2" },
  P3: { labelKey: "ticketPriorityP3", className: "text-priority-p3" },
};

/** The priority entry, or null for a missing or unknown priority: show no mark. */
export function resolvePriority(
  priority: string | null | undefined
): (PriorityMeta & { priority: TicketPriority }) | null {
  if (!priority || !(TICKET_PRIORITIES as readonly string[]).includes(priority)) return null;
  const known = priority as TicketPriority;
  return { priority: known, ...PRIORITY_META[known] };
}

/** The incident priorities, which the incident banner and paging count. */
export function isIncidentPriority(priority: string | null | undefined): boolean {
  return priority === "P1" || priority === "P2" || priority === "P3";
}

export const MILESTONE_HEALTHS = ["ON_TRACK", "AT_RISK", "OVERDUE", "DONE"] as const;
export type MilestoneHealth = (typeof MILESTONE_HEALTHS)[number];

type HealthMeta = {
  labelKey: TranslationKey;
  dotClassName: string;
  pillClassName: string;
};

const HEALTH_META: Record<MilestoneHealth, HealthMeta> = {
  ON_TRACK: {
    labelKey: "milestoneHealthOnTrack",
    dotClassName: "bg-success",
    pillClassName: "bg-success-light",
  },
  AT_RISK: {
    labelKey: "milestoneHealthAtRisk",
    dotClassName: "bg-warning",
    pillClassName: "bg-warning-light",
  },
  OVERDUE: {
    labelKey: "milestoneHealthOverdue",
    dotClassName: "bg-destructive",
    pillClassName: "bg-error-light",
  },
  DONE: {
    labelKey: "milestoneHealthDone",
    dotClassName: "bg-status-done",
    pillClassName: "bg-muted",
  },
};

/** The health entry, or null for a missing or unknown health. */
export function resolveHealth(
  health: string | null | undefined
): (HealthMeta & { health: MilestoneHealth }) | null {
  if (!health || !(MILESTONE_HEALTHS as readonly string[]).includes(health)) return null;
  const known = health as MilestoneHealth;
  return { health: known, ...HEALTH_META[known] };
}

/** The effort sizes of a ticket, smallest first; M is the default of a new ticket. */
export const MISSION_RANKS = ["XS", "S", "M", "L", "XL", "XXL"] as const;
export type MissionRank = (typeof MISSION_RANKS)[number];
export const DEFAULT_MISSION_RANK: MissionRank = "M";

export function isMissionRank(value: string | null | undefined): value is MissionRank {
  return !!value && (MISSION_RANKS as readonly string[]).includes(value);
}

const HEX_COLOUR = /^#(?:[0-9a-f]{3}|[0-9a-f]{6})$/i;

/** A label's colour from the API when it is a valid hex colour, otherwise null. */
export function labelColour(colour: string | null | undefined): string | null {
  return colour && HEX_COLOUR.test(colour.trim()) ? colour.trim() : null;
}
