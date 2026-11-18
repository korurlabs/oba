import { describe, expect, it } from "bun:test";

import { dueDateInstant } from "@/components/ticket/dueDateModel";
import {
  BACKLOG_PLACEMENT,
  canPickStatus,
  createTicketBody,
  defaultBoardId,
  defaultPriority,
  descriptionTooLong,
  initialPlacement,
  openSprints,
  priorityForType,
  rankOrDefault,
  statusForPlacement,
  statusOrDefault,
  titleProblem,
  typeOrDefault,
  type NewTicketDraft,
} from "@/components/ticket/newTicketModel";
import {
  newTicketHref,
  newTicketSheetHref,
  parseNewTicketPreset,
} from "@/components/ticket/ticketRoutes";
import type { Sprint } from "@/lib/schemas/boards";

const sprint = (id: string, status: string, startDate: string | null = null): Sprint => ({
  id,
  name: id,
  slug: null,
  status,
  startDate,
  endDate: null,
});

const draft = (overrides: Partial<NewTicketDraft> = {}): NewTicketDraft => ({
  title: "  Fix login  ",
  description: "",
  type: "FEATURE",
  priority: "MEDIUM",
  missionRank: "M",
  placement: BACKLOG_PLACEMENT,
  assigneeIds: ["me"],
  labelIds: [],
  ...overrides,
});

describe("defaults", () => {
  it("starts an incident at P2 and every other type at Medium", () => {
    expect(defaultPriority("INCIDENT")).toBe("P2");
    expect(defaultPriority("BUG")).toBe("MEDIUM");
  });

  it("keeps a priority the new type allows and resets one it does not", () => {
    expect(priorityForType("BUG", "HIGH")).toBe("HIGH");
    expect(priorityForType("INCIDENT", "HIGH")).toBe("P2");
    expect(priorityForType("INCIDENT", "P1")).toBe("P1");
    expect(priorityForType("FEATURE", "P1")).toBe("MEDIUM");
    expect(priorityForType("FEATURE", null)).toBe("MEDIUM");
  });

  it("falls back to Feature and M for unknown values", () => {
    expect(typeOrDefault("INCIDENT")).toBe("INCIDENT");
    expect(typeOrDefault("TASK")).toBe("FEATURE");
    expect(typeOrDefault(null)).toBe("FEATURE");
    expect(rankOrDefault("XL")).toBe("XL");
    expect(rankOrDefault("M2")).toBe("M");
  });
});

describe("placement", () => {
  const sprints: Sprint[] = [
    sprint("done", "COMPLETED", "2026-10-01T00:00:00Z"),
    sprint("next", "PLANNED", "2026-11-20T00:00:00Z"),
    sprint("now", "ACTIVE", "2026-11-06T00:00:00Z"),
  ];

  it("offers the active sprint first, then planned ones, never a completed one", () => {
    expect(openSprints(sprints).map((item) => item.id)).toEqual(["now", "next"]);
  });

  it("starts on the requested open sprint, else the backlog", () => {
    expect(initialPlacement(sprints, "now")).toBe("now");
    expect(initialPlacement(sprints, "done")).toBe(BACKLOG_PLACEMENT);
    expect(initialPlacement(sprints, "other-board")).toBe(BACKLOG_PLACEMENT);
    expect(initialPlacement(sprints, null)).toBe(BACKLOG_PLACEMENT);
  });
});

describe("defaultBoardId", () => {
  const rows = [{ id: "a" }, { id: "b" }, { id: "c" }];

  it("takes the requested board, then the most recent known one, then the first", () => {
    expect(defaultBoardId("b", ["c"], rows)).toBe("b");
    expect(defaultBoardId(null, ["gone", "c"], rows)).toBe("c");
    expect(defaultBoardId("gone", [], rows)).toBe("a");
    expect(defaultBoardId(null, [], [])).toBeNull();
  });

  it("trusts the requested board while the tree is not loaded", () => {
    expect(defaultBoardId("b", [], [])).toBe("b");
  });
});

describe("field checks", () => {
  it("requires a title of at most 255 characters", () => {
    expect(titleProblem("   ")).toBe("required");
    expect(titleProblem("x".repeat(256))).toBe("too-long");
    expect(titleProblem(` ${"x".repeat(255)} `)).toBeNull();
  });

  it("limits the description to 10,000 characters", () => {
    expect(descriptionTooLong("x".repeat(10_000))).toBe(false);
    expect(descriptionTooLong("x".repeat(10_001))).toBe(true);
  });
});

describe("status", () => {
  it("lets a sprint ticket and a sub-ticket pick a status, never a backlog ticket", () => {
    expect(canPickStatus("s1", false)).toBe(true);
    expect(canPickStatus(BACKLOG_PLACEMENT, true)).toBe(true);
    expect(canPickStatus(BACKLOG_PLACEMENT, false)).toBe(false);
  });

  it("sends the picked status where it can be picked, else Todo", () => {
    expect(statusForPlacement("IN_PROGRESS", "s1", false)).toBe("IN_PROGRESS");
    expect(statusForPlacement("IN_PROGRESS", BACKLOG_PLACEMENT, false)).toBe("TODO");
    expect(statusForPlacement("IN_PROGRESS", BACKLOG_PLACEMENT, true)).toBe("IN_PROGRESS");
    expect(statusForPlacement("NOT_A_STATUS", "s1", false)).toBe("TODO");
    expect(statusForPlacement(null, "s1", false)).toBe("TODO");
  });
});

describe("createTicketBody", () => {
  it("sends a backlog ticket as Todo with the backlog id and a trimmed title", () => {
    expect(createTicketBody(draft(), { backlogId: "bl", parentTicketId: null })).toEqual({
      title: "Fix login",
      type: "FEATURE",
      priority: "MEDIUM",
      status: "TODO",
      missionRank: "M",
      backlogId: "bl",
      assigneeIds: ["me"],
      labelIds: [],
    });
  });

  it("sends a picked due day as an instant and leaves it out without one", () => {
    const target = { backlogId: "bl", parentTicketId: null };
    expect(createTicketBody(draft({ dueDay: "2026-11-04" }), target)?.dueDate).toBe(
      dueDateInstant("2026-11-04") ?? "missing"
    );
    expect(createTicketBody(draft({ dueDay: null }), target)?.dueDate).toBeUndefined();
  });

  it("sends a sprint ticket with the sprint id and no backlog id", () => {
    const body = createTicketBody(draft({ placement: "s1", description: " Steps " }), {
      backlogId: "bl",
      parentTicketId: null,
    });
    expect(body?.sprintId).toBe("s1");
    expect(body?.backlogId).toBeUndefined();
    expect(body?.description).toBe("Steps");
  });

  it("sends a sub-ticket with only its parent, which decides where it lands", () => {
    const body = createTicketBody(draft({ placement: "s1" }), {
      backlogId: "bl",
      parentTicketId: "p1",
    });
    expect(body?.parentTicketId).toBe("p1");
    expect(body?.sprintId).toBeUndefined();
    expect(body?.backlogId).toBeUndefined();
  });

  it("refuses a backlog ticket while the backlog id is unknown", () => {
    expect(createTicketBody(draft(), { backlogId: null, parentTicketId: null })).toBeNull();
  });

  it("sends the picked status, milestone and project of a sprint ticket", () => {
    const body = createTicketBody(
      draft({ placement: "s1", status: "IN_REVIEW", milestoneId: "ms1", projectId: "p1" }),
      { backlogId: "bl", parentTicketId: null }
    );
    expect(body?.status).toBe("IN_REVIEW");
    expect(body?.milestoneId).toBe("ms1");
    expect(body?.projectId).toBe("p1");
  });

  it("sends a backlog ticket as Todo whatever status was picked", () => {
    const body = createTicketBody(draft({ status: "IN_REVIEW" }), {
      backlogId: "bl",
      parentTicketId: null,
    });
    expect(body?.status).toBe("TODO");
    expect(body?.milestoneId).toBeUndefined();
    expect(body?.projectId).toBeUndefined();
  });

  it("corrects a priority that does not fit the type", () => {
    const body = createTicketBody(draft({ type: "INCIDENT", priority: "HIGH" }), {
      backlogId: "bl",
      parentTicketId: null,
    });
    expect(body?.priority).toBe("P2");
  });
});

describe("newTicketHref", () => {
  it("leaves empty preset values out and reads them back", () => {
    const href = newTicketHref({ boardId: "b1", sprintId: null, type: "INCIDENT", priority: "" });
    expect(href).toEqual({ pathname: "/ticket/new", params: { boardId: "b1", type: "INCIDENT" } });
    expect(parseNewTicketPreset(href.params)).toEqual({
      boardId: "b1",
      sprintId: null,
      status: null,
      parentKey: null,
      type: "INCIDENT",
      priority: null,
    });
  });

  it("carries the board, sprint and status of a Kanban column's add button", () => {
    const href = newTicketHref({ boardId: "b1", sprintId: "s1", status: "IN_REVIEW" });
    expect(href).toEqual({
      pathname: "/ticket/new",
      params: { boardId: "b1", sprintId: "s1", status: "IN_REVIEW" },
    });
    expect(parseNewTicketPreset(href.params)).toEqual({
      boardId: "b1",
      sprintId: "s1",
      status: "IN_REVIEW",
      parentKey: null,
      type: null,
      priority: null,
    });
  });

  it("reads the first value of a repeated param and treats an empty one as unset", () => {
    expect(parseNewTicketPreset({ sprintId: ["s2", "s3"], status: "" })).toMatchObject({
      sprintId: "s2",
      status: null,
    });
  });
});

describe("statusOrDefault", () => {
  it("keeps a ticket status", () => {
    expect(statusOrDefault("IN_PROGRESS")).toBe("IN_PROGRESS");
    expect(statusOrDefault("DONE")).toBe("DONE");
  });

  it("starts at Todo without a status or with an unknown one", () => {
    expect(statusOrDefault(null)).toBe("TODO");
    expect(statusOrDefault("in_progress")).toBe("TODO");
    expect(statusOrDefault("ARCHIVED")).toBe("TODO");
  });

  it("is still sent as Todo when the ticket lands in the backlog", () => {
    expect(statusForPlacement(statusOrDefault("IN_REVIEW"), BACKLOG_PLACEMENT, false)).toBe("TODO");
    expect(statusForPlacement(statusOrDefault("IN_REVIEW"), "s1", false)).toBe("IN_REVIEW");
  });
});

describe("newTicketSheetHref", () => {
  it("points a draft field sheet under the New ticket route", () => {
    expect(newTicketSheetHref("assignees")).toEqual({ pathname: "/ticket/new/assignees" });
    expect(newTicketSheetHref("description")).toEqual({ pathname: "/ticket/new/description" });
  });
});
