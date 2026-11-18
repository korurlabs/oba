import { describe, expect, it } from "bun:test";

import {
  addBoardMemberHref,
  addChildBoardsHref,
  boardMembersHref,
  boardMilestonesHref,
  boardOptionFormHref,
  boardOptionsHref,
  bulkMoveHref,
  childBoardsHref,
  deleteBoardHref,
  editSprintHref,
  milestoneFormHref,
  newChildBoardHref,
} from "@/components/boards/boardRoutes";

describe("boardOptionsHref", () => {
  it("pushes the Labels or Projects screen of the board", () => {
    expect(boardOptionsHref("b1", "labels")).toEqual({
      pathname: "/boards/board/[boardId]/labels",
      params: { boardId: "b1" },
    });
    expect(boardOptionsHref("b1", "projects")).toEqual({
      pathname: "/boards/board/[boardId]/projects",
      params: { boardId: "b1" },
    });
  });
});

describe("boardOptionFormHref", () => {
  it("opens an empty sheet for a new option", () => {
    expect(boardOptionFormHref("b1", "labels")).toEqual({
      pathname: "/boards/board/[boardId]/label-form",
      params: { boardId: "b1" },
    });
    expect(boardOptionFormHref("b1", "projects")).toEqual({
      pathname: "/boards/board/[boardId]/project-form",
      params: { boardId: "b1" },
    });
  });

  it("names the edited option by the param of its kind", () => {
    expect(boardOptionFormHref("b1", "labels", "l1")).toEqual({
      pathname: "/boards/board/[boardId]/label-form",
      params: { boardId: "b1", labelId: "l1" },
    });
    expect(boardOptionFormHref("b1", "projects", "p1")).toEqual({
      pathname: "/boards/board/[boardId]/project-form",
      params: { boardId: "b1", projectId: "p1" },
    });
  });
});

describe("boardMilestonesHref", () => {
  it("pushes the Milestones screen of the board", () => {
    expect(boardMilestonesHref("b1")).toEqual({
      pathname: "/boards/board/[boardId]/milestones",
      params: { boardId: "b1" },
    });
  });
});

describe("milestoneFormHref", () => {
  it("opens an empty sheet for a new milestone", () => {
    expect(milestoneFormHref("b1")).toEqual({
      pathname: "/boards/board/[boardId]/milestone-form",
      params: { boardId: "b1" },
    });
  });

  it("names the edited milestone", () => {
    expect(milestoneFormHref("b1", "m1")).toEqual({
      pathname: "/boards/board/[boardId]/milestone-form",
      params: { boardId: "b1", milestoneId: "m1" },
    });
  });
});

describe("child board hrefs", () => {
  it("pushes the Child boards screen of the board", () => {
    expect(childBoardsHref("b1")).toEqual({
      pathname: "/boards/board/[boardId]/child-boards",
      params: { boardId: "b1" },
    });
  });

  it("opens Add existing boards for the board", () => {
    expect(addChildBoardsHref("b1")).toEqual({
      pathname: "/boards/board/[boardId]/add-child-boards",
      params: { boardId: "b1" },
    });
  });

  it("opens New board with the board as the fixed parent", () => {
    expect(newChildBoardHref("b1")).toEqual({
      pathname: "/boards/new-board",
      params: { parentBoardId: "b1" },
    });
  });
});

describe("deleteBoardHref", () => {
  it("opens the Delete board sheet of the board", () => {
    expect(deleteBoardHref("b1")).toEqual({
      pathname: "/boards/board/[boardId]/delete-board",
      params: { boardId: "b1" },
    });
  });
});

describe("bulkMoveHref", () => {
  it("opens the Move selected tickets sheet of the board", () => {
    expect(bulkMoveHref("b1")).toEqual({
      pathname: "/boards/board/[boardId]/bulk-move",
      params: { boardId: "b1" },
    });
  });
});

describe("member hrefs", () => {
  it("pushes the Members screen of the board", () => {
    expect(boardMembersHref("b1")).toEqual({
      pathname: "/boards/board/[boardId]/members",
      params: { boardId: "b1" },
    });
  });

  it("opens Add member for the board", () => {
    expect(addBoardMemberHref("b1")).toEqual({
      pathname: "/boards/board/[boardId]/add-member",
      params: { boardId: "b1" },
    });
  });
});

describe("editSprintHref", () => {
  it("opens Edit sprint on General by default", () => {
    expect(editSprintHref("b1", "s1")).toEqual({
      pathname: "/boards/board/[boardId]/edit-sprint",
      params: { boardId: "b1", sprintId: "s1", tab: "general" },
    });
  });

  it("opens Edit sprint on the Boards tab", () => {
    expect(editSprintHref("b1", "s1", "boards").params.tab).toBe("boards");
  });
});
