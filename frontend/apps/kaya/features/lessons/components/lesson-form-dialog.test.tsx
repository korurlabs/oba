import { afterAll, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";

import type { LessonDetailResponse } from "../schemas";

// The dialog's content sits in a portal that a static render leaves out, so the
// dialog shows it in place; the hooks are replaced. The form body is real.
const actualDialog = { ...(await import("@oba/ui/sky/dialog")) };
const actualLessons = { ...(await import("../hooks/use-lessons")) };
const actualSessions = { ...(await import("@/features/sessions/hooks/use-sessions")) };
const passthrough = ({ children }: { children?: ReactNode }) => <div>{children}</div>;
const mutation = { isPending: false, mutate: () => undefined };

mock.module("@oba/ui/sky/dialog", () => ({
  ...actualDialog,
  Dialog: passthrough,
  DialogTrigger: ({ children }: { children: ReactNode }) => <>{children}</>,
  DialogContent: passthrough,
  DialogHeader: passthrough,
  DialogFooter: passthrough,
  DialogTitle: ({ children }: { children: ReactNode }) => <h2>{children}</h2>,
  DialogDescription: ({ children }: { children: ReactNode }) => <p>{children}</p>,
}));
mock.module("../hooks/use-lessons", () => ({
  ...actualLessons,
  useCreateLesson: () => mutation,
  useUpdateLesson: () => mutation,
}));
mock.module("@/features/sessions/hooks/use-sessions", () => ({
  ...actualSessions,
  useRepositories: () => ({
    error: null,
    data: {
      data: [
        { id: "r-b", name: "beta", status: "ACTIVE" },
        { id: "r-a", name: "alpha", status: "ACTIVE" },
        { id: "r-x", name: "archived", status: "ARCHIVED" },
      ],
    },
  }),
}));
afterAll(() => {
  mock.module("@oba/ui/sky/dialog", () => actualDialog);
  mock.module("../hooks/use-lessons", () => actualLessons);
  mock.module("@/features/sessions/hooks/use-sessions", () => actualSessions);
});

const { LessonFormDialog } = await import("./lesson-form-dialog");

const LESSON: LessonDetailResponse = {
  id: "l-1",
  title: "Run Flyway first",
  body: "Apply the migration.",
  repository: { id: "r-b", name: "beta" },
  user: { id: "u-1", name: "Ada" },
  claudeSessionId: null,
  session: null,
  createdAt: "2026-10-25T09:00:00Z",
  updatedAt: "2026-10-25T09:00:00Z",
  canEdit: true,
};

const trigger = <button type="button">Open</button>;
/** The labels of the rendered `<option>` elements, in order. */
const options = (html: string): string[] =>
  Array.from(html.matchAll(/<option[^>]*>([^<]*)<\/option>/g), (match) => match[1] ?? "");

describe("LessonFormDialog", () => {
  test("without a lesson it creates: empty fields, Create, organization-wide chosen", () => {
    const html = renderToStaticMarkup(<LessonFormDialog trigger={trigger} />);
    expect(html).toContain("<h2>New lesson</h2>");
    expect(html).toMatch(/<button[^>]*type="submit"[^>]*>Create<\/button>/);
    expect(html).toContain('placeholder="Markdown"');
    expect(options(html)).toEqual(["Organization-wide", "alpha", "beta"]);
  });

  test("with a lesson it edits: prefilled title and text, Save, its repository selected", () => {
    const html = renderToStaticMarkup(<LessonFormDialog lesson={LESSON} trigger={trigger} />);
    expect(html).toContain("<h2>Edit lesson</h2>");
    expect(html).toContain('value="Run Flyway first"');
    expect(html).toContain("Apply the migration.");
    expect(html).toMatch(/<button[^>]*type="submit"[^>]*>Save<\/button>/);
    expect(html).toMatch(
      /<option[^>]*value="r-b"[^>]*selected|<option[^>]*selected[^>]*value="r-b"/
    );
  });

  test("the inputs carry the server's length limits", () => {
    const html = renderToStaticMarkup(<LessonFormDialog trigger={trigger} />);
    expect(html).toContain('maxLength="300"');
    expect(html).toContain('maxLength="4000"');
  });
});
