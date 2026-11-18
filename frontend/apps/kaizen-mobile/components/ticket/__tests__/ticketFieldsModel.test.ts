import { describe, expect, it } from "bun:test";

import { dueDateInstant, dueDayOf } from "@/components/ticket/dueDateModel";
import { BACKLOG_PLACEMENT } from "@/components/ticket/newTicketModel";
import {
  currentPlacement,
  descriptionForSave,
  labelIdsOf,
  labelsFor,
  milestoneFor,
  projectFor,
  sprintChoices,
} from "@/components/ticket/ticketFieldsModel";
import { addDaysToDateOnly, toDateOnly } from "@/lib/dates";
import type { Milestone, Sprint } from "@/lib/schemas/boards";

const sprint = (id: string, status: string, startDate: string | null = null): Sprint => ({
  id,
  name: `Sprint ${id}`,
  slug: null,
  status,
  startDate,
  endDate: null,
});

describe("dueDateInstant", () => {
  it("sends the end of the picked day in the viewer's zone as a UTC instant", () => {
    const sent: string | null = dueDateInstant("2026-11-04");
    expect(sent).not.toBeNull();
    expect(sent?.endsWith("Z")).toBe(true);
    const moment: Date = new Date(sent as string);
    // Read back on the viewer's calendar it is the picked day, and one
    // millisecond later the next day starts.
    expect(toDateOnly(moment)).toBe("2026-11-04");
    expect(toDateOnly(new Date(moment.getTime() + 1))).toBe(addDaysToDateOnly("2026-11-04", 1));
  });

  it("refuses a value that is not a day", () => {
    expect(dueDateInstant("2026-02-30")).toBeNull();
    expect(dueDateInstant("")).toBeNull();
    expect(dueDateInstant(null)).toBeNull();
  });

  it("round-trips through dueDayOf", () => {
    expect(dueDayOf({ dueDate: dueDateInstant("2026-12-31") })).toBe("2026-12-31");
    expect(dueDayOf({ dueDate: null })).toBeNull();
  });
});

describe("sprintChoices", () => {
  it("offers the backlog, then the active and planned sprints, never a completed one", () => {
    const choices = sprintChoices([
      sprint("old", "COMPLETED"),
      sprint("next", "PLANNED", "2026-11-17T00:00:00Z"),
      sprint("now", "ACTIVE", "2026-11-03T00:00:00Z"),
    ]);
    expect(choices.map((choice) => choice.id)).toEqual([BACKLOG_PLACEMENT, "now", "next"]);
    expect(choices[0]?.sprint).toBeNull();
  });

  it("names where the ticket is now", () => {
    expect(currentPlacement({ sprint: { id: "now", name: "Sprint now" } })).toBe("now");
    expect(currentPlacement({ sprint: null })).toBe(BACKLOG_PLACEMENT);
  });
});

describe("labels", () => {
  const options = [
    { id: "api", name: "API", color: "#ff0000" },
    { id: "ux", name: "UX", color: null },
  ];

  it("reads the label ids of a ticket", () => {
    expect(labelIdsOf({ labels: options })).toEqual(["api", "ux"]);
    expect(labelIdsOf({ labels: null })).toEqual([]);
  });

  it("shows the picked labels in pick order and keeps a label the board dropped", () => {
    const current = [{ id: "gone", name: "Gone", color: null }];
    expect(labelsFor(["ux", "gone", "unknown"], options, current)).toEqual([
      { id: "ux", name: "UX", color: null },
      { id: "gone", name: "Gone", color: null },
    ]);
    expect(labelsFor([], options, current)).toEqual([]);
  });
});

describe("milestone and project", () => {
  const milestones: Milestone[] = [
    { id: "beta", name: "Mobile beta", status: "IN_PROGRESS", targetDate: "2026-12-01" },
  ];

  it("builds the milestone the ticket carries", () => {
    expect(milestoneFor("beta", milestones)).toEqual({
      id: "beta",
      name: "Mobile beta",
      status: "IN_PROGRESS",
      targetDate: "2026-12-01",
    });
    expect(milestoneFor(null, milestones)).toBeNull();
    expect(milestoneFor("missing", milestones)).toBeNull();
  });

  it("builds the project the ticket carries", () => {
    const projects = [{ id: "mobile", name: "Mobile", color: "#00ff00" }];
    expect(projectFor("mobile", projects)).toEqual({
      id: "mobile",
      name: "Mobile",
      color: "#00ff00",
    });
    expect(projectFor("missing", projects)).toBeNull();
  });
});

describe("descriptionForSave", () => {
  it("keeps the text as written and clears a blank one", () => {
    expect(descriptionForSave("  # Title\n\nBody  ")).toBe("  # Title\n\nBody  ");
    expect(descriptionForSave("  \n ")).toBe("");
  });
});
