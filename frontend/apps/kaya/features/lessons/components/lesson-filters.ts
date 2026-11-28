import type { FilterCategory, FilterOption } from "@oba/ui/sky/filter-popover";

import { withChosen } from "@/components/common/list-parts";

import { ORGANIZATION_WIDE, type LessonFilterParams } from "../hooks/use-lesson-filter-params";
import type { LessonFiltersResponse, LessonReferenceResponse } from "../schemas";
import { ORGANIZATION_WIDE_LABEL, referenceName } from "./lesson-data";

/** Every filter of the lesson list cleared, the search included. */
export const NO_LESSON_FILTERS: Partial<LessonFilterParams> = {
  search: null,
  repositoryId: null,
  userId: null,
};

function referenceOptions(
  references: readonly LessonReferenceResponse[] | undefined
): FilterOption[] {
  return (references ?? []).map((reference: LessonReferenceResponse) => ({
    value: reference.id,
    label: referenceName(reference),
  }));
}

/**
 * The lesson list's filter categories: the repositories and users that hold
 * lessons, and "Organization-wide" for the lessons of no repository.
 */
export function lessonFilterCategories(
  filters: LessonFilterParams,
  onChange: (change: Partial<LessonFilterParams>) => void,
  options: LessonFiltersResponse | undefined
): FilterCategory[] {
  return [
    {
      key: "repositoryId",
      label: "Repository",
      mode: "single",
      searchable: true,
      options: withChosen(
        [
          { value: ORGANIZATION_WIDE, label: ORGANIZATION_WIDE_LABEL },
          ...referenceOptions(options?.repositories),
        ],
        filters.repositoryId
      ),
      value: filters.repositoryId,
      onChange: (repositoryId: string | null) => onChange({ repositoryId }),
    },
    {
      key: "userId",
      label: "User",
      mode: "single",
      searchable: true,
      options: withChosen(referenceOptions(options?.users), filters.userId),
      value: filters.userId,
      onChange: (userId: string | null) => onChange({ userId }),
    },
  ];
}
