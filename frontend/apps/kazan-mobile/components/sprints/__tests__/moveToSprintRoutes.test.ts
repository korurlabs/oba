import { describe, expect, it } from "bun:test";

import {
  boardMoveToSprintHref,
  parseMoveToSprintParams,
  ticketMoveToSprintHref,
} from "@/components/sprints/moveToSprintRoutes";

describe("move to sprint routes", () => {
  it("leaves out an empty preset", () => {
    expect(ticketMoveToSprintHref("KZN-1")).toEqual({
      pathname: "/ticket/[ticketKey]/move-to-sprint",
      params: { ticketKey: "KZN-1" },
    });
  });

  it("round-trips the preset through the board route", () => {
    const href = boardMoveToSprintHref("board", "KZN-1", {
      sprintId: "sprint",
      pickSubTickets: true,
    });
    expect(href.params).toEqual({
      boardId: "board",
      ticketKey: "KZN-1",
      sprintId: "sprint",
      subTickets: "pick",
    });
    expect(parseMoveToSprintParams(href.params)).toEqual({
      ticketKey: "KZN-1",
      sprintId: "sprint",
      pickSubTickets: true,
    });
  });

  it("reads missing params as no preset", () => {
    expect(parseMoveToSprintParams({ ticketKey: ["KZN-2"] })).toEqual({
      ticketKey: "KZN-2",
      sprintId: null,
      pickSubTickets: false,
    });
  });
});
