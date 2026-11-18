import type { RepositoryResponse } from "@/features/sessions/schemas";

import { ORGANIZATION_WIDE, type LessonFilterParams } from "../hooks/use-lesson-filter-params";
import { LESSONS_PAGE_SIZE } from "../hooks/use-lessons";
import type {
  LessonQueryRequest,
  LessonReferenceResponse,
  LessonSessionResponse,
} from "../schemas";

/** The label of a lesson that belongs to no repository, in selects. */
export const ORGANIZATION_WIDE_LABEL = "Organization-wide";

/** The URL filters as the list request; an unknown page falls back to the first. */
export function toLessonQuery(filters: LessonFilterParams): LessonQueryRequest {
  const query: LessonQueryRequest = {
    page: Math.max(1, filters.page),
    amount: LESSONS_PAGE_SIZE,
  };
  if (filters.repositoryId === ORGANIZATION_WIDE) query.organizationWide = true;
  else if (filters.repositoryId !== null && filters.repositoryId !== "")
    query.repositoryId = filters.repositoryId;
  if (filters.userId !== null && filters.userId !== "") query.userId = filters.userId;
  const search = filters.search?.trim() ?? "";
  if (search !== "") query.search = search;
  return query;
}

/** Whether a filter narrows the list. */
export function hasActiveFilter(filters: LessonFilterParams): boolean {
  return filters.repositoryId !== null || filters.userId !== null || filters.search !== null;
}

/** The text of an empty list; a page past the last can be empty after another member's delete. */
export function emptyLessonsText(filters: LessonFilterParams): string {
  if (hasActiveFilter(filters)) return "No lesson matches these filters.";
  if (filters.page > 1) return "This page holds no lesson.";
  return "No lesson recorded yet.";
}

/** A reference's name, or a short id when the name is no longer known. */
export function referenceName(reference: LessonReferenceResponse): string {
  return reference.name ?? reference.id.slice(0, 8);
}

/** The repository column's text: a lesson without a repository applies everywhere. */
export function repositoryText(repository: LessonReferenceResponse | null | undefined): string {
  return repository ? referenceName(repository) : "All";
}

/** The first line of a lesson's text that holds anything, which the list shows under the title. */
export function firstLine(body: string): string {
  return (
    body
      .split("\n")
      .map((line: string) => line.trim())
      .find((line: string) => line !== "") ?? ""
  );
}

/** A session's title, or a short id for a session without one. */
export function sessionText(session: LessonSessionResponse): string {
  return session.title?.trim() || session.sessionId.slice(0, 8);
}

/**
 * The repositories a lesson can move to. The server refuses a move to an
 * archived repository, so those are left out, except the one the lesson already
 * has: an edit that keeps it stays allowed, and a list that has not loaded yet
 * must not drop it.
 */
export function repositoryChoices(
  repositories: readonly RepositoryResponse[],
  current: LessonReferenceResponse | null | undefined
): { id: string; name: string }[] {
  const choices = repositories
    .filter(
      (repository: RepositoryResponse) =>
        repository.status === "ACTIVE" || repository.id === current?.id
    )
    .map(({ id, name }: RepositoryResponse) => ({ id, name }));
  if (current && !choices.some((choice) => choice.id === current.id)) {
    choices.push({ id: current.id, name: referenceName(current) });
  }
  return choices.sort((left, right) => left.name.localeCompare(right.name));
}
