import { afterAll, describe, expect, mock, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

import { KayaApiError } from "@/lib/api/errors";

import type { LessonListResponse } from "../schemas";

let state: { data?: { data: LessonListResponse[] }; error: unknown };
let asked: unknown[] = [];

const ROW: LessonListResponse = {
  id: "l-1",
  title: "Run Flyway first",
  body: "Apply the migration.",
  repository: { id: "r-1", name: "village" },
  user: { id: "u-1", name: "Ada" },
  claudeSessionId: "sess-1",
  session: { id: "s-1", sessionId: "sess-1", title: "Fix tests" },
  createdAt: "2026-10-25T09:00:00Z",
  updatedAt: "2026-10-25T09:00:00Z",
  canEdit: false,
};

const actualLessons = { ...(await import("../hooks/use-lessons")) };
mock.module("../hooks/use-lessons", () => ({
  ...actualLessons,
  useLessons: (params: unknown) => {
    asked.push(params);
    return state;
  },
}));
afterAll(() => {
  mock.module("../hooks/use-lessons", () => actualLessons);
});

const { SessionLessonsPanel } = await import("./session-lessons-panel");

function render(): string {
  return renderToStaticMarkup(<SessionLessonsPanel sessionId="sess-1" />);
}

describe("SessionLessonsPanel", () => {
  test("asks for the lessons of the session, the largest page", () => {
    state = { data: { data: [] }, error: null };
    asked = [];
    render();
    expect(asked[0]).toEqual({ sessionId: "sess-1", page: 1, amount: 100 });
  });

  test("lists each lesson as a link with its repository", () => {
    state = {
      data: {
        data: [ROW, { ...ROW, id: "l/2", title: "Second", repository: null }],
      },
      error: null,
    };
    const html = render();
    expect(html).toContain('href="/lessons/l-1"');
    expect(html).toContain("Run Flyway first");
    expect(html).toContain('href="/lessons/l%2F2"');
    expect(html).toContain("village");
    expect(html).toContain("All");
  });

  test("a session without lessons says so", () => {
    state = { data: { data: [] }, error: null };
    expect(render()).toContain("This session recorded no lesson.");
  });

  test("while loading it says so", () => {
    state = { data: undefined, error: null };
    expect(render()).toContain("Loading the lessons");
  });

  test("a failed first load shows the refusal", () => {
    state = { data: undefined, error: new KayaApiError("Boom", 500, null) };
    const html = render();
    expect(html).toContain("The lessons of this session could not be loaded");
    expect(html).toContain('role="alert"');
  });

  test("a failed reload keeps the rows and warns", () => {
    state = { data: { data: [ROW] }, error: new KayaApiError("Boom", 500, null) };
    const html = render();
    expect(html).toContain("The latest reload failed");
    expect(html).toContain("Run Flyway first");
  });
});
