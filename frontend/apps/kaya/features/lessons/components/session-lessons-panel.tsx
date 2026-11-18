"use client";

import Link from "next/link";

import { describeError } from "@/lib/api/error-text";
import { dateTimeText } from "@/lib/format-time";

import { useLessons } from "../hooks/use-lessons";
import { LESSON_LIMITS, type LessonListResponse } from "../schemas";
import { repositoryText } from "./lesson-data";
import { ReloadWarning } from "./lesson-parts";

/** The lessons a session recorded, newest first, each linking to its page. */
export function SessionLessonsPanel({ sessionId }: { sessionId: string }) {
  const lessons = useLessons({ sessionId, page: 1, amount: LESSON_LIMITS.pageSizeMax });
  const rows = lessons.data?.data;
  if (lessons.error !== null && rows == null) {
    return (
      <p className="text-error text-sm" role="alert">
        The lessons of this session could not be loaded: {describeError(lessons.error)}
      </p>
    );
  }
  if (rows == null) {
    return <p className="text-muted-foreground text-sm">Loading the lessons</p>;
  }
  if (rows.length === 0) {
    return <p className="text-muted-foreground text-sm">This session recorded no lesson.</p>;
  }
  return (
    <div className="flex flex-col gap-2">
      {lessons.error !== null && <ReloadWarning error={lessons.error} />}
      <ul className="flex flex-col divide-y rounded-md border">
        {rows.map((lesson: LessonListResponse) => (
          <li key={lesson.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 px-3 py-2">
            <Link
              href={`/lessons/${encodeURIComponent(lesson.id)}`}
              className="min-w-0 flex-1 font-medium break-words hover:underline"
            >
              {lesson.title}
            </Link>
            <span className="text-muted-foreground text-xs">
              {repositoryText(lesson.repository)}
            </span>
            <span className="text-muted-foreground text-xs">{dateTimeText(lesson.createdAt)}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
