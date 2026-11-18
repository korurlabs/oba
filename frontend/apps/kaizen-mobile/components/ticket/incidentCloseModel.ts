/*
 * The rule that an incident documents its cause before it is closed: the API
 * refuses to set an INCIDENT to DONE while its root cause analysis or lessons
 * learned is blank (422 `KAI_VAL_063`). The app asks for both first, in the
 * Close incident sheet, so the close is not sent only to be refused.
 */

/** The two texts the API requires before an incident may be set to Done. */
export interface IncidentCloseValues {
  rca: string;
  lessonsLearned: string;
}

/** The texts a ticket already holds, as the detail carries them. */
export interface StoredIncidentTexts {
  rca?: string | null;
  lessonsLearned?: string | null;
}

/**
 * True when a status change sets an incident to Done. An incident that is
 * already Done is not asked again, so incidents closed before the rule stay
 * editable, as the API allows.
 */
export function requiresIncidentClose(
  type: string | undefined,
  currentStatus: string | undefined,
  newStatus: string | undefined
): boolean {
  return type === "INCIDENT" && newStatus === "DONE" && currentStatus !== "DONE";
}

/** True when both texts hold more than white space. */
export function hasIncidentCloseTexts(stored: StoredIncidentTexts | null | undefined): boolean {
  return !!stored?.rca?.trim() && !!stored?.lessonsLearned?.trim();
}

/**
 * True when a change of a ticket whose texts are known must ask for them
 * first: it sets an incident to Done and the ticket lacks one of the texts.
 * A board list carries no texts, so a board move asks whenever
 * `requiresIncidentClose` holds, with the stored texts filled in.
 */
export function needsIncidentCloseTexts(
  type: string | undefined,
  currentStatus: string | undefined,
  newStatus: string | undefined,
  stored: StoredIncidentTexts | null | undefined
): boolean {
  return requiresIncidentClose(type, currentStatus, newStatus) && !hasIncidentCloseTexts(stored);
}

/**
 * The texts the sheet starts with: typed ones that were not saved yet win
 * over the stored ones, so a failed close loses nothing.
 */
export function incidentClosePrefill(
  draft: Partial<IncidentCloseValues> | undefined,
  stored: StoredIncidentTexts | null | undefined
): IncidentCloseValues {
  return {
    rca: draft?.rca ?? stored?.rca ?? "",
    lessonsLearned: draft?.lessonsLearned ?? stored?.lessonsLearned ?? "",
  };
}

/** The trimmed texts, or null while either is blank. */
export function incidentCloseToSend(values: IncidentCloseValues): IncidentCloseValues | null {
  const rca: string = values.rca.trim();
  const lessonsLearned: string = values.lessonsLearned.trim();
  if (!rca || !lessonsLearned) return null;
  return { rca, lessonsLearned };
}
