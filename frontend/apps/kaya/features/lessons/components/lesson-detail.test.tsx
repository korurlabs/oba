import { afterAll, describe, expect, mock, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

import { KayaApiError } from "@/lib/api/errors";

import type { LessonDetailResponse } from "../schemas";

// The detail reads the lesson through the lesson hooks and navigates with the router;
// those are replaced, the rendering is real.
let state: { data?: { data: LessonDetailResponse }; error: unknown };

const LESSON: LessonDetailResponse = {
  id: "l-1",
  title: "Run Flyway first",
  body: "Apply the migration.\n\n<b>not markup</b>",
  repository: { id: "r-1", name: "village" },
  user: { id: "u-1", name: "Ada" },
  claudeSessionId: "sess-abcdef123",
  session: { id: "s-1", sessionId: "sess-abcdef123", title: "Fix tests" },
  createdAt: "2026-10-25T09:00:00Z",
  updatedAt: "2026-10-25T10:00:00Z",
  canEdit: false,
};

const actualLessons = { ...(await import("../hooks/use-lessons")) };
const actualNavigation = { ...(await import("next/navigation")) };
const mutation = { isPending: false, mutate: () => undefined, mutateAsync: async () => undefined };

mock.module("../hooks/use-lessons", () => ({
  ...actualLessons,
  useLesson: () => ({ ...state, refetch: async () => undefined }),
  useDeleteLesson: () => mutation,
  useCreateLesson: () => mutation,
  useUpdateLesson: () => mutation,
}));
mock.module("next/navigation", () => ({
  ...actualNavigation,
  useRouter: () => ({ push: () => undefined }),
}));

afterAll(() => {
  mock.module("../hooks/use-lessons", () => actualLessons);
  mock.module("next/navigation", () => actualNavigation);
});

const { LessonDetail } = await import("./lesson-detail");

function render(lesson: LessonDetailResponse): string {
  state = { data: { data: lesson }, error: null };
  return renderToStaticMarkup(<LessonDetail lessonId={lesson.id} />);
}

const button = (label: string) => new RegExp(`<button[^>]*>(<svg.*?</svg>)?${label}</button>`);

describe("Edit and Delete follow canEdit", () => {
  test("without canEdit neither button shows", () => {
    const html = render({ ...LESSON, canEdit: false });
    expect(html).not.toMatch(button("Edit"));
    expect(html).not.toMatch(button("Delete"));
    expect(html).toContain("Run Flyway first");
  });

  test("with canEdit both buttons show", () => {
    const html = render({ ...LESSON, canEdit: true });
    expect(html).toMatch(button("Edit"));
    expect(html).toMatch(button("Delete"));
  });
});

describe("LessonDetail", () => {
  test("shows the repository, the session link, the author and the text as text", () => {
    const html = render(LESSON);
    expect(html).toContain("village");
    expect(html).toContain('href="/sessions/sess-abcdef123"');
    expect(html).toContain("Fix tests");
    expect(html).toContain("Ada");
    expect(html).toContain("&lt;b&gt;not markup&lt;/b&gt;");
    expect(html).not.toContain("<b>not markup</b>");
  });

  test("a lesson of the whole organization reads All and a lesson without a session has no Session entry", () => {
    const html = render({ ...LESSON, repository: null, session: null });
    expect(html).toContain("All");
    expect(html).not.toContain(">Session<");
  });

  test("while loading it shows the loading state", () => {
    state = { data: undefined, error: null };
    expect(renderToStaticMarkup(<LessonDetail lessonId="l-1" />)).toContain("Loading the lesson");
  });

  test("a 404 says the lesson does not exist", () => {
    state = { data: undefined, error: new KayaApiError("missing", 404, "KAY_NF_001") };
    const html = renderToStaticMarkup(<LessonDetail lessonId="l-1" />);
    expect(html).toContain("This lesson does not exist");
  });

  test("another failure says the lesson could not be loaded", () => {
    state = { data: undefined, error: new KayaApiError("Boom", 500, null) };
    expect(renderToStaticMarkup(<LessonDetail lessonId="l-1" />)).toContain(
      "Could not load the lesson"
    );
  });

  test("a failed reload keeps the lesson and warns", () => {
    state = { data: { data: LESSON }, error: new KayaApiError("Boom", 500, null) };
    const html = renderToStaticMarkup(<LessonDetail lessonId="l-1" />);
    expect(html).toContain("The latest reload failed");
    expect(html).toContain("Run Flyway first");
  });
});
