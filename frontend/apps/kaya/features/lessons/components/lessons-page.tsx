"use client";

import Link from "next/link";
import { Plus } from "lucide-react";

import { Button } from "@oba/ui/sky/button";
import { FilterPopover } from "@oba/ui/sky/filter-popover";
import { ListToolbar } from "@oba/ui/sky/list-toolbar";
import { PageHeading } from "@oba/ui/sky/page-heading";
import { ListEmptyState, LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { SearchInput } from "@oba/ui/sky/search-input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@oba/ui/sky/table";
import {
  CLEAR_FILTERS_LABEL,
  CLEAR_SEARCH_LABEL,
  FILTER_LABELS,
  ListPagination,
} from "@/components/common/list-parts";
import { describeError } from "@/lib/api/error-text";
import { ageText } from "@/lib/format-time";
import { useNow } from "@/lib/use-now";

import { useLessonFilterParams, type LessonFilterParams } from "../hooks/use-lesson-filter-params";
import { useLessonFilters, useLessons } from "../hooks/use-lessons";
import { LESSON_LIMITS, type LessonListResponse } from "../schemas";
import {
  emptyLessonsText,
  firstLine,
  hasActiveFilter,
  referenceName,
  repositoryText,
  toLessonQuery,
} from "./lesson-data";
import { NO_LESSON_FILTERS, lessonFilterCategories } from "./lesson-filters";
import { LessonFormDialog } from "./lesson-form-dialog";
import { LessonSessionLink, ReloadWarning } from "./lesson-parts";

export type LessonRowsProps = {
  lessons: readonly LessonListResponse[];
  now: number;
};

/** The lessons as table rows, in the order the API answers: newest first. */
export function LessonRows({ lessons, now }: LessonRowsProps) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Title</TableHead>
          <TableHead>Repository</TableHead>
          <TableHead>Session</TableHead>
          <TableHead>User</TableHead>
          <TableHead>Created</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {lessons.map((lesson: LessonListResponse) => (
          <TableRow key={lesson.id} className="align-top">
            <TableCell className="max-w-md font-medium break-words whitespace-normal">
              <Link href={`/lessons/${encodeURIComponent(lesson.id)}`} className="hover:underline">
                {lesson.title}
              </Link>
              <div className="text-muted-foreground truncate text-xs font-normal">
                {firstLine(lesson.body)}
              </div>
            </TableCell>
            <TableCell className="whitespace-nowrap">{repositoryText(lesson.repository)}</TableCell>
            <TableCell>
              {lesson.session && <LessonSessionLink session={lesson.session} />}
            </TableCell>
            <TableCell className="whitespace-nowrap">{referenceName(lesson.user)}</TableCell>
            <TableCell className="whitespace-nowrap">{ageText(lesson.createdAt, now)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

/** The organization's lessons, newest first: the rules agents read at session start. */
export function LessonsPage() {
  const [filters, setFilters] = useLessonFilterParams();
  const lessons = useLessons(toLessonQuery(filters));
  const options = useLessonFilters();
  const now = useNow(60_000);
  const rows = lessons.data?.data ?? [];
  const pagination = lessons.data?.pagination ?? null;

  const onChange = (change: Partial<LessonFilterParams>) => void setFilters({ ...change, page: 1 });
  const newLesson = (
    <LessonFormDialog
      trigger={
        <Button>
          <Plus aria-hidden />
          New lesson
        </Button>
      }
    />
  );

  return (
    <div className="flex flex-col gap-6">
      <PageHeading
        title="Lessons"
        description="Shared rules: agents see new ones by title at session start. Delete one that is wrong or obsolete."
      />
      <ListToolbar
        search={
          <SearchInput
            value={filters.search}
            onSearchChange={(search: string | undefined) =>
              // The API takes a search of at most this length.
              onChange({ search: search?.slice(0, LESSON_LIMITS.searchMax) ?? null })
            }
            label="Search lessons"
            placeholder="Title or text"
            clearLabel={CLEAR_SEARCH_LABEL}
          />
        }
        filters={
          <FilterPopover
            categories={lessonFilterCategories(filters, onChange, options.data?.data ?? undefined)}
            onClear={() => onChange({ ...NO_LESSON_FILTERS, search: filters.search })}
            labels={FILTER_LABELS}
          />
        }
        create={newLesson}
      />
      {options.error !== null && (
        <p className="text-warning-text text-sm" role="status">
          The filter options could not be loaded: {describeError(options.error)}
        </p>
      )}
      {lessons.error !== null && lessons.data !== undefined && (
        <ReloadWarning error={lessons.error} />
      )}
      {lessons.error !== null && lessons.data === undefined ? (
        <LoadError
          title="Could not load the lessons"
          message={describeError(lessons.error)}
          onRetry={() => void lessons.refetch()}
        />
      ) : lessons.data === undefined ? (
        <TableLoading label="Loading the lessons" />
      ) : rows.length === 0 ? (
        <ListEmptyState
          filtered={hasActiveFilter(filters) || filters.page > 1}
          onClearFilters={() => onChange(NO_LESSON_FILTERS)}
          create={newLesson}
          labels={{
            emptyTitle: "No lessons yet",
            emptyDescription: emptyLessonsText(filters),
            filteredTitle: "No lesson matches",
            filteredDescription: emptyLessonsText(filters),
            clearFilters: CLEAR_FILTERS_LABEL,
          }}
        />
      ) : (
        <LessonRows lessons={rows} now={now} />
      )}
      <ListPagination
        pagination={pagination}
        noun="lessons"
        onPageChange={(page: number) => void setFilters({ page })}
      />
    </div>
  );
}
