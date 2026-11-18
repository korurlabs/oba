import { describe, expect, it } from "bun:test";

import {
  EMPTY_UPDATE_DRAFT,
  addDraftLines,
  calendarWeeks,
  canManageUpdate,
  canSeeNotPosted,
  draftFromUpdate,
  hasUpdateDraftProblems,
  lineFromTicketOption,
  lineSetsTicketProgress,
  minutesUntilDeadline,
  missingMemberName,
  monthOf,
  nudgeRefusal,
  parsePercentage,
  postedCount,
  postedOnTimeCount,
  readChatDailyUpdate,
  resolveUpdateFilter,
  shiftMonth,
  splitMissingMembers,
  storedDay,
  ticketsNotOnDraft,
  toUpdateWriteRequest,
  updateAuthorName,
  updateDraftProblems,
  updateFilterCount,
  updateTextLines,
  type UpdateDraft,
} from "@/components/updates/updateModel";
import type { UpdateResponse, UpdateTicketOption } from "@/lib/schemas/updates";

const TODAY = "2026-11-09";

const UPDATE: UpdateResponse = {
  id: "update-1",
  createdAt: "2026-11-09T06:45:00Z",
  editedAt: null,
  author: { userId: "user-1", email: "ayse@example.com", firstName: "Ayşe", lastName: "Kaya" },
  focus: "Ship the card\n\n  Review PRs ",
  questions: null,
  items: [
    {
      ticketId: "t-1",
      boardId: "b-1",
      boardName: "Kaizen",
      ticketKey: "KZN-1",
      ticketTitle: "Cards",
      percentage: 40,
      note: "halfway",
      progressSynced: true,
      canOpen: true,
      missionRank: "M",
    },
    {
      ticketId: null,
      boardId: "b-1",
      boardName: "Kaizen",
      ticketKey: "KZN-2",
      ticketTitle: "Removed",
      percentage: 10,
      note: null,
      progressSynced: false,
      canOpen: false,
      missionRank: null,
    },
  ],
  finishedTickets: [],
  summaryText: "KZN-1 Cards 40%",
};

function option(id: string, percentage: number): UpdateTicketOption {
  return {
    id,
    key: `KZN-${id}`,
    title: `Ticket ${id}`,
    percentage,
    board: { id: "b-1", name: "Kaizen" },
  };
}

describe("update filter", () => {
  it("follows today while no day is stored and refuses a future day", () => {
    expect(resolveUpdateFilter({ day: null, personId: null }, TODAY)).toEqual({
      day: TODAY,
      personId: null,
    });
    expect(resolveUpdateFilter({ day: "2026-11-10", personId: "u" }, TODAY)).toEqual({
      day: TODAY,
      personId: "u",
    });
    expect(resolveUpdateFilter({ day: "2026-11-02", personId: null }, TODAY).day).toBe(
      "2026-11-02"
    );
    expect(resolveUpdateFilter({ day: "not a day", personId: null }, TODAY).day).toBe(TODAY);
  });

  it("stores today as null, so tomorrow opens on the new today", () => {
    expect(storedDay(TODAY, TODAY)).toBeNull();
    expect(storedDay("2026-11-06", TODAY)).toBe("2026-11-06");
  });

  it("counts a past day and a person on the badge", () => {
    expect(updateFilterCount({ day: TODAY, personId: null }, TODAY)).toBe(0);
    expect(updateFilterCount({ day: "2026-11-06", personId: null }, TODAY)).toBe(1);
    expect(updateFilterCount({ day: "2026-11-06", personId: "u" }, TODAY)).toBe(2);
  });

  it("shows Not posted to owners and admins only", () => {
    expect(canSeeNotPosted("OWNER")).toBe(true);
    expect(canSeeNotPosted("ADMIN")).toBe(true);
    expect(canSeeNotPosted("MEMBER")).toBe(false);
    expect(canSeeNotPosted("GUEST")).toBe(false);
    expect(canSeeNotPosted(null)).toBe(false);
  });
});

describe("update cards", () => {
  it("reads the update of a chat entry and ignores plain or deleted messages", () => {
    expect(
      readChatDailyUpdate({
        id: "m",
        dailyUpdateId: "update-1",
        dailyUpdate: UPDATE,
        deleted: false,
      })?.id
    ).toBe("update-1");
    expect(
      readChatDailyUpdate({ id: "m", dailyUpdateId: null, dailyUpdate: null, deleted: false })
    ).toBeNull();
    expect(
      readChatDailyUpdate({
        id: "m",
        dailyUpdateId: "update-1",
        dailyUpdate: UPDATE,
        deleted: true,
      })
    ).toBeNull();
  });

  it("returns null for an update that does not match the schema", () => {
    const original = console.error;
    console.error = () => {};
    try {
      expect(
        readChatDailyUpdate({
          id: "m",
          dailyUpdateId: "u",
          dailyUpdate: { id: "u" },
          deleted: false,
        })
      ).toBeNull();
    } finally {
      console.error = original;
    }
  });

  it("lets only the author edit and delete", () => {
    expect(canManageUpdate(UPDATE, "user-1")).toBe(true);
    expect(canManageUpdate(UPDATE, "user-2")).toBe(false);
    expect(canManageUpdate(UPDATE, undefined)).toBe(false);
  });

  it("names the author, else the email", () => {
    expect(updateAuthorName(UPDATE.author, "?")).toBe("Ayşe Kaya");
    expect(
      updateAuthorName({ userId: "u", email: "a@b.c", firstName: null, lastName: null }, "?")
    ).toBe("a@b.c");
    expect(updateAuthorName({ userId: "u" }, "?")).toBe("?");
  });

  it("makes one bullet per non-blank line", () => {
    expect(updateTextLines(UPDATE.focus)).toEqual(["Ship the card", "Review PRs"]);
    expect(updateTextLines(null)).toEqual([]);
  });
});

describe("calendar", () => {
  it("starts weeks on Monday and disables days after today", () => {
    // November 2026 starts on a Sunday.
    const weeks = calendarWeeks("2026-11-01", TODAY);
    expect(weeks[0].map((cell) => cell.day)).toEqual([
      null,
      null,
      null,
      null,
      null,
      null,
      "2026-11-01",
    ]);
    const cells = weeks.flat().filter((cell) => cell.day);
    expect(cells).toHaveLength(30);
    expect(cells.find((cell) => cell.day === TODAY)?.disabled).toBe(false);
    expect(cells.find((cell) => cell.day === "2026-11-10")?.disabled).toBe(true);
    expect(weeks.every((week) => week.length === 7)).toBe(true);
  });

  it("moves between months on the calendar", () => {
    expect(monthOf("2026-11-09")).toBe("2026-11-01");
    expect(shiftMonth("2026-11-01", -1)).toBe("2026-10-01");
    expect(shiftMonth("2026-12-01", 1)).toBe("2027-01-01");
  });
});

describe("update form", () => {
  it("starts a picked ticket at its own progress", () => {
    expect(lineFromTicketOption(option("1", 37.6))).toMatchObject({
      percentage: "38",
      ticketPercentage: 38,
    });
    expect(lineFromTicketOption(option("1", 140)).percentage).toBe("100");
  });

  it("adds tickets once each, up to the limit", () => {
    const first = addDraftLines(EMPTY_UPDATE_DRAFT, [
      option("1", 0),
      option("1", 0),
      option("2", 50),
    ]);
    expect(first.added).toBe(2);
    expect(first.draft.lines.map((line) => line.ticketId)).toEqual(["1", "2"]);
    const many: UpdateTicketOption[] = Array.from({ length: 40 }, (_, index) =>
      option(String(index), 0)
    );
    expect(addDraftLines(EMPTY_UPDATE_DRAFT, many).draft.lines).toHaveLength(30);
    expect(
      ticketsNotOnDraft(first.draft, [option("2", 0), option("3", 0)]).map((item) => item.id)
    ).toEqual(["3"]);
  });

  it("drops a line whose ticket was deleted when editing", () => {
    const draft: UpdateDraft = draftFromUpdate(UPDATE);
    expect(draft.lines.map((line) => line.ticketId)).toEqual(["t-1"]);
    expect(draft.lines[0]).toMatchObject({
      percentage: "40",
      note: "halfway",
      ticketPercentage: null,
    });
    expect(draft.focus).toBe(UPDATE.focus as string);
    expect(draft.questions).toBe("");
  });

  it("accepts whole percentages from 0 to 100 only", () => {
    expect(parsePercentage("0")).toBe(0);
    expect(parsePercentage(" 100 ")).toBe(100);
    expect(parsePercentage("101")).toBeNull();
    expect(parsePercentage("4.5")).toBeNull();
    expect(parsePercentage("")).toBeNull();
    expect(parsePercentage("-1")).toBeNull();
  });

  it("says when saving sets the ticket's progress", () => {
    const line = lineFromTicketOption(option("1", 40));
    expect(lineSetsTicketProgress(line)).toBe(false);
    expect(lineSetsTicketProgress({ ...line, percentage: "60" })).toBe(true);
    expect(lineSetsTicketProgress({ ...line, ticketPercentage: null, percentage: "60" })).toBe(
      false
    );
  });

  it("blocks an empty form and wrong fields, as the server would", () => {
    expect(updateDraftProblems(EMPTY_UPDATE_DRAFT).tickets).toBe("updFormTicketsRequired");
    const line = lineFromTicketOption(option("1", 40));
    const draft: UpdateDraft = {
      lines: [{ ...line, percentage: "abc", note: "x".repeat(501) }],
      focus: "y".repeat(2001),
      questions: "",
    };
    const problems = updateDraftProblems(draft);
    expect(problems.lines["1"]).toEqual({
      percentage: "updFormPercentageRange",
      note: "updFormNoteTooLong",
    });
    expect(problems.focus).toBe("updFormTextTooLong");
    expect(hasUpdateDraftProblems(problems)).toBe(true);
    expect(
      hasUpdateDraftProblems(updateDraftProblems({ lines: [line], focus: "", questions: "" }))
    ).toBe(false);
  });

  it("sends blanks as null and percentages as numbers", () => {
    const line = { ...lineFromTicketOption(option("1", 40)), note: "  " };
    expect(toUpdateWriteRequest({ lines: [line], focus: "Focus", questions: " " })).toEqual({
      tickets: [{ ticketId: "1", percentage: 40, note: null }],
      focus: "Focus",
      questions: null,
    });
    expect(() =>
      toUpdateWriteRequest({ lines: [{ ...line, percentage: "x" }], focus: "", questions: "" })
    ).toThrow();
  });
});

describe("not posted", () => {
  const report = {
    expectedCount: 8,
    members: [
      { userId: "u1", email: "a@x.io", firstName: "Ali", lastName: null, postedAt: null },
      { userId: "u2", email: "b@x.io", firstName: null, lastName: null, postedAt: null },
      {
        userId: "u3",
        email: "c@x.io",
        firstName: "Can",
        lastName: "Er",
        postedAt: "2026-11-09T07:45:00Z",
      },
    ],
  };

  it("splits those with nothing from those who posted late", () => {
    const { notPosted, late } = splitMissingMembers(report);
    expect(notPosted.map((member) => member.userId)).toEqual(["u1", "u2"]);
    expect(late.map((member) => member.userId)).toEqual(["u3"]);
    expect(postedOnTimeCount(report)).toBe(5);
    expect(postedCount(report)).toBe(6);
    expect(missingMemberName(report.members[1])).toBe("b@x.io");
  });

  it("counts whole minutes to the deadline and none after it", () => {
    const deadline = "2026-11-09T07:30:00Z";
    expect(minutesUntilDeadline(deadline, new Date("2026-11-09T07:00:30Z"))).toBe(30);
    expect(minutesUntilDeadline(deadline, new Date("2026-11-09T07:30:00Z"))).toBeNull();
    expect(minutesUntilDeadline("bad", new Date())).toBeNull();
  });

  it("explains each nudge refusal and marks an earlier nudge as sent", () => {
    expect(nudgeRefusal("DLY_STA_001")).toEqual({
      messageKey: "updNudgeAlreadySent",
      markNudged: true,
      refresh: false,
    });
    expect(nudgeRefusal("DLY_VAL_002")).toMatchObject({
      messageKey: "updNudgeRecipientPosted",
      refresh: true,
    });
    expect(nudgeRefusal("DLY_NOT_001")).toMatchObject({
      messageKey: "updNudgeRecipientNotFound",
      refresh: true,
    });
    expect(nudgeRefusal("DLY_VAL_001").messageKey).toBe("updNudgeSelf");
    expect(nudgeRefusal("ORG_SEC_003").messageKey).toBe("updNudgeNotAllowed");
    expect(nudgeRefusal(undefined).messageKey).toBe("updNudgeFailed");
  });
});
