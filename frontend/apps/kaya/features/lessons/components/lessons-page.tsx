"use client";

import Link from "next/link";
import { Plus } from "lucide-react";

import { Button } from "@oba/ui/sky/button";
import { InfoTip } from "@oba/ui/sky/info-tip";
import { EmptyState, LoadError, TableLoading } from "@oba/ui/sky/page-states";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@oba/ui/sky/table";
import { describeError } from "@/lib/api/error-text";
import { ageText } from "@/lib/format-time";
import { useNow } from "@/lib/use-now";

import { useLessonFilterParams, type LessonFilterParams } from "../hooks/use-lesson-filter-params";
import { useLessonFilters, useLessons } from "../hooks/use-lessons";
import type { LessonListResponse } from "../schemas";
import {
  emptyLessonsText,
  firstLine,
  referenceName,
  repositoryText,
  toLessonQuery,
} from "./lesson-data";
import { LessonFilters } from "./lesson-filters";
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

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <h1>Lessons</h1>
          <InfoTip label="What is a lesson?">
            Lessons are shared rules. Agents see new ones by title at session start. Delete one that
            is wrong or obsolete.
          </InfoTip>
        </div>
        <LessonFormDialog
          trigger={
            <Button>
              <Plus aria-hidden />
              New lesson
            </Button>
          }
        />
      </div>
      <LessonFilters
        filters={filters}
        onChange={(change: Partial<LessonFilterParams>) => void setFilters({ ...change, page: 1 })}
        options={options.data?.data ?? undefined}
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
        <EmptyState title="No lesson" description={emptyLessonsText(filters)} />
      ) : (
        <LessonRows lessons={rows} now={now} />
      )}
      {pagination !== null && pagination.totalPages > 1 && (
        <nav aria-label="Pages" className="flex items-center justify-between gap-4 text-sm">
          <Button
            variant="outline"
            disabled={pagination.first}
            onClick={() => void setFilters({ page: pagination.currentPage - 1 })}
          >
            Newer
          </Button>
          <span className="text-muted-foreground">
            Page {pagination.currentPage} of {pagination.totalPages} · {pagination.totalElements}{" "}
            lessons
          </span>
          <Button
            variant="outline"
            disabled={pagination.last}
            onClick={() => void setFilters({ page: pagination.currentPage + 1 })}
          >
            Older
          </Button>
        </nav>
      )}
    </div>
  );
}
