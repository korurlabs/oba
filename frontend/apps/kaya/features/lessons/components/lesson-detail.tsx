"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import type { ReactNode } from "react";
import { ArrowLeft, Pencil, Trash2 } from "lucide-react";

import { Button } from "@oba/ui/sky/button";
import { PageHeading } from "@oba/ui/sky/page-heading";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { Markdown } from "@/features/sessions/components/markdown";
import { describeError } from "@/lib/api/error-text";
import { ERROR_CODES, KayaApiError } from "@/lib/api/errors";
import { dateTimeText } from "@/lib/format-time";

import { useDeleteLesson, useLesson } from "../hooks/use-lessons";
import type { LessonDetailResponse } from "../schemas";
import { referenceName, repositoryText } from "./lesson-data";
import { LessonFormDialog } from "./lesson-form-dialog";
import { LessonSessionLink, ReloadWarning } from "./lesson-parts";

function Meta({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex min-w-0 flex-col gap-0.5">
      <dt className="text-muted-foreground text-xs">{label}</dt>
      <dd className="text-sm break-words">{children}</dd>
    </div>
  );
}

function BackLink() {
  return (
    <Link
      href="/lessons"
      className="text-muted-foreground hover:text-foreground flex w-fit items-center gap-1 text-sm"
    >
      <ArrowLeft className="size-4" aria-hidden />
      All lessons
    </Link>
  );
}

/** Deletes the lesson for good, then leaves its page, which would otherwise read a 404. */
function DeleteLesson({ lessonId }: { lessonId: string }) {
  const router = useRouter();
  const remove = useDeleteLesson();
  return (
    <ConfirmDialog
      trigger={
        <Button variant="outline" size="sm">
          <Trash2 aria-hidden />
          Delete
        </Button>
      }
      title="Delete this lesson"
      description="It is deleted for everyone in the organization and agents no longer see it. This cannot be undone."
      confirmLabel="Delete"
      destructive
      onConfirm={async () => {
        await remove.mutateAsync(lessonId);
        router.push("/lessons");
      }}
    />
  );
}

function LessonView({ lesson }: { lesson: LessonDetailResponse }) {
  return (
    <div className="flex flex-col gap-6">
      <PageHeading
        title={lesson.title}
        back={{ href: "/lessons", label: "All lessons" }}
        actions={
          lesson.canEdit && (
            <>
              <LessonFormDialog
                lesson={lesson}
                trigger={
                  <Button variant="outline" size="sm">
                    <Pencil aria-hidden />
                    Edit
                  </Button>
                }
              />
              <DeleteLesson lessonId={lesson.id} />
            </>
          )
        }
        description={
          <dl className="mt-2 grid grid-cols-2 gap-x-6 gap-y-2 sm:grid-cols-3 lg:grid-cols-5">
            <Meta label="Repository">{repositoryText(lesson.repository)}</Meta>
            {lesson.session && (
              <Meta label="Session">
                <LessonSessionLink session={lesson.session} />
              </Meta>
            )}
            <Meta label="User">{referenceName(lesson.user)}</Meta>
            <Meta label="Created">{dateTimeText(lesson.createdAt)}</Meta>
            <Meta label="Updated">{dateTimeText(lesson.updatedAt)}</Meta>
          </dl>
        }
      />
      <Markdown source={lesson.body} />
    </div>
  );
}

/**
 * One lesson with its text as markdown. Edit and Delete show only with
 * `canEdit`; the server still refuses anyone else.
 */
export function LessonDetail({ lessonId }: { lessonId: string }) {
  const lesson = useLesson(lessonId);
  const detail = lesson.data?.data;
  if (lesson.error !== null && detail === undefined) {
    const gone =
      lesson.error instanceof KayaApiError && lesson.error.code === ERROR_CODES.NOT_FOUND;
    return (
      <div className="flex flex-col gap-4">
        <BackLink />
        <LoadError
          title={gone ? "This lesson does not exist" : "Could not load the lesson"}
          message={describeError(lesson.error)}
          onRetry={() => void lesson.refetch()}
        />
      </div>
    );
  }
  if (!detail) return <TableLoading label="Loading the lesson" />;
  return (
    <div className="flex flex-col gap-3">
      {lesson.error !== null && <ReloadWarning error={lesson.error} />}
      <LessonView lesson={detail} />
    </div>
  );
}
