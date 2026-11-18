import type { DependencyResponse } from "@/features/links/schemas";

/** "Frontmatter, 3 fields": the closed line of a file's frontmatter. */
export function frontmatterSummary(fields: number): string {
  return `Frontmatter, ${fields} ${fields === 1 ? "field" : "fields"}`;
}

/** A tab's label with its count after it, or the label alone while the count is unknown. */
export function tabLabel(label: string, count: number | null): string {
  return count === null ? label : `${label} ${count}`;
}

/** How many entries the Links tab lists: what the file links to and what uses it. */
export function linksCount(dependencies: DependencyResponse | null | undefined): number | null {
  if (dependencies == null) return null;
  return dependencies.linksTo.length + dependencies.usedBy.length;
}

/**
 * How many versions the History tab lists, known only once its last page is
 * loaded: an older page can still hold versions of the file.
 */
export function historyCount(entries: number, complete: boolean): number | null {
  return complete ? entries : null;
}

/**
 * The folders and name of a file that is not its document's main file, such
 * as a part of a skill, so the reader sees where it sits; null for the main
 * file itself and for a document that has only one.
 */
export function breadcrumbOf(path: string, mainPath: string | null | undefined): string[] | null {
  if (mainPath == null || mainPath === path) return null;
  return path.split("/").filter((segment: string) => segment !== "");
}
