import {
  VISIBLE_ASSIGNEES,
  visibleAssignees,
  type AssigneeOption,
} from "@/components/boards/boardForms";

/**
 * A name or query folded for matching: case, accents and the Turkish dotted
 * and dotless i are ignored, so "ayse" finds "Ayşe" and "isik" finds "Işık".
 */
export function foldForSearch(value: string): string {
  return value.normalize("NFD").replace(/\p{M}/gu, "").toLowerCase().replace(/ı/g, "i").trim();
}

/** Whether a person's name contains every word of the query, in any order. */
export function matchesPerson(name: string, query: string): boolean {
  const words: string[] = foldForSearch(query).split(/\s+/).filter(Boolean);
  if (words.length === 0) return true;
  const folded: string = foldForSearch(name);
  return words.every((word) => folded.includes(word));
}

/** A board with more members than the first chips shows a member search. */
export function showsAssigneeSearch(memberCount: number): boolean {
  return memberCount > VISIBLE_ASSIGNEES;
}

/**
 * The assignee chips to show. With a query, every matching person and no
 * "+ N more"; without one, the first few plus every selected person.
 */
export function assigneeChoices(
  options: readonly AssigneeOption[],
  selected: readonly string[],
  view: { expanded: boolean; query: string }
): { shown: AssigneeOption[]; hidden: number } {
  if (foldForSearch(view.query)) {
    return {
      shown: options.filter((option) => matchesPerson(option.name, view.query)),
      hidden: 0,
    };
  }
  return visibleAssignees(options, selected, view.expanded);
}

/** The confirm of the Filters sheet: it is blocked only once the count is known to be 0. */
export function filterConfirmState(count: number | null): { disabled: boolean; noMatch: boolean } {
  const noMatch: boolean = count === 0;
  return { disabled: noMatch, noMatch };
}
