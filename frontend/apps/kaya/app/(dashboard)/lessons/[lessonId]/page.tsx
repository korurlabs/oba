import type { Metadata } from "next";

import { LessonDetail } from "@/features/lessons/components";

export const metadata: Metadata = {
  title: "Lesson",
};

/** One lesson with its text, repository, author and session. */
export default async function LessonRoute({ params }: { params: Promise<{ lessonId: string }> }) {
  const { lessonId } = await params;
  return <LessonDetail lessonId={lessonId} />;
}
