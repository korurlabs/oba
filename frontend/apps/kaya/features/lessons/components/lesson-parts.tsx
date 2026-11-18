import Link from "next/link";

import { describeError } from "@/lib/api/error-text";

import type { LessonSessionResponse } from "../schemas";
import { sessionText } from "./lesson-data";

/** A link to the session that recorded a lesson; the session page takes the Claude Code id. */
export function LessonSessionLink({ session }: { session: LessonSessionResponse }) {
  const text = sessionText(session);
  return (
    <Link
      href={`/sessions/${encodeURIComponent(session.sessionId)}`}
      title={text}
      className="block max-w-48 truncate hover:underline"
    >
      {text}
    </Link>
  );
}

/** Says that a reload failed, so rows kept on screen are not taken for the current answer. */
export function ReloadWarning({ error }: { error: unknown }) {
  return (
    <p className="text-warning-text text-sm" role="status">
      The latest reload failed, showing the previous data: {describeError(error)}
    </p>
  );
}
