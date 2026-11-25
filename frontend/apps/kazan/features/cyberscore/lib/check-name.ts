/**
 * A check name may start with a short reference such as "ID-01", the id the
 * check carries in the report it came from. The list shows that reference in
 * its own mono column, so a reader can scan by id; a name without one keeps
 * its whole text as the title.
 */
const REFERENCE_PATTERN = /^([A-Z][A-Z0-9]{0,7}-\d{1,4})\s+(\S.*)$/s;

export interface CheckNameParts {
  reference: string | null;
  title: string;
}

export function splitCheckName(name: string): CheckNameParts {
  const [, reference, title] = REFERENCE_PATTERN.exec(name.trim()) ?? [];
  return reference && title ? { reference, title } : { reference: null, title: name };
}
