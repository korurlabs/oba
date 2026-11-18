import { describe, expect, it } from "bun:test";

import { flattenBoardTree, type BoardRow } from "@/components/boards/boardModel";
import {
  addExistingBoardRows,
  addExistingPreview,
  childBoardRows,
} from "@/components/boards/settings/boardSettingsModel";
import { boardTreeItemSchema } from "@/lib/schemas/boards";
import { sprintChildBoardListSchema } from "@/lib/schemas/sprints";

/*
 * Bodies captured from the local API as `dev`: a parent with a child created
 * under it ("M4 Child") and an existing board with its own child ("M4
 * Existing") added through `PUT /children`. The descendant came along.
 */
const TREE_AFTER_ADD: unknown = {
  id: "42fce3c3-f0bc-4cd7-b6de-3d554244a5c9",
  createdAt: "2026-11-11T11:00:01.287516Z",
  updatedAt: "2026-11-11T11:00:01.287516Z",
  name: "M4 Parent",
  description: null,
  prefix: "MFP",
  slug: "m4-parent",
  color: "#3B82F6",
  icon: null,
  childBoards: [
    {
      id: "28306a77-8ba9-459e-bffb-79ad3e21569e",
      createdAt: "2026-11-11T11:00:01.330158Z",
      updatedAt: "2026-11-11T11:00:01.330158Z",
      name: "M4 Child",
      description: null,
      prefix: "MFC",
      slug: "m4-child",
      color: "#3B82F6",
      icon: null,
      childBoards: [],
    },
    {
      id: "e0cebd93-6391-458a-87a5-9590987030c8",
      createdAt: "2026-11-11T11:00:01.341943Z",
      updatedAt: "2026-11-11T11:00:01.399313Z",
      name: "M4 Existing",
      description: null,
      prefix: "MFX",
      slug: "m4-existing",
      color: "#3B82F6",
      icon: null,
      childBoards: [
        {
          id: "9f6ae40b-7508-4489-9a67-45b5d7133818",
          createdAt: "2026-11-11T11:00:01.354838Z",
          updatedAt: "2026-11-11T11:00:01.354838Z",
          name: "M4 Existing Kid",
          description: null,
          prefix: "MFY",
          slug: "m4-existing-kid",
          color: "#3B82F6",
          icon: null,
          childBoards: [],
        },
      ],
    },
  ],
};

const SPRINT_CHILD_BOARDS_ENVELOPE: unknown = {
  success: true,
  message: "Sprint child boards retrieved successfully",
  data: [
    {
      id: "28306a77-8ba9-459e-bffb-79ad3e21569e",
      name: "M4 Child",
      prefix: "MFC",
      color: "#3B82F6",
      parentBoardId: "42fce3c3-f0bc-4cd7-b6de-3d554244a5c9",
      depth: 1,
      canLink: true,
      activeSprintId: null,
      activeSprintName: null,
    },
    {
      id: "e0cebd93-6391-458a-87a5-9590987030c8",
      name: "M4 Existing",
      prefix: "MFX",
      color: "#3B82F6",
      parentBoardId: "42fce3c3-f0bc-4cd7-b6de-3d554244a5c9",
      depth: 1,
      canLink: true,
      activeSprintId: null,
      activeSprintName: null,
    },
    {
      id: "9f6ae40b-7508-4489-9a67-45b5d7133818",
      name: "M4 Existing Kid",
      prefix: "MFY",
      color: "#3B82F6",
      parentBoardId: "e0cebd93-6391-458a-87a5-9590987030c8",
      depth: 2,
      canLink: true,
      activeSprintId: null,
      activeSprintName: null,
    },
  ],
  pagination: null,
  error: null,
};

const PARENT_ID = "42fce3c3-f0bc-4cd7-b6de-3d554244a5c9";

describe("child boards from a real board tree", () => {
  const rows: BoardRow[] = flattenBoardTree([boardTreeItemSchema.parse(TREE_AFTER_ADD)]);

  it("lists every descendant indented per level, direct children marked", () => {
    expect(
      childBoardRows(rows, PARENT_ID).map((row) => [row.board.name, row.indent, row.direct])
    ).toEqual([
      ["M4 Child", 0, true],
      ["M4 Existing", 0, true],
      ["M4 Existing Kid", 1, false],
    ]);
  });

  it("offers none of the board's own subtree to Add existing", () => {
    expect(addExistingBoardRows(rows, PARENT_ID)).toEqual([]);
  });

  it("previews a picked board with its descendant included", () => {
    const existingId: string = rows.find((row) => row.name === "M4 Existing")!.id;
    expect(
      addExistingPreview(rows, [existingId]).map((row) => [row.board.name, row.picked, row.indent])
    ).toEqual([
      ["M4 Existing", true, 0],
      ["M4 Existing Kid", false, 1],
    ]);
  });
});

describe("sprint child boards from the API", () => {
  it("parses the envelope Create sprint lists", () => {
    const envelope = SPRINT_CHILD_BOARDS_ENVELOPE as { data: unknown };
    const boards = sprintChildBoardListSchema.parse(envelope.data);
    expect(boards.map((board) => [board.name, board.depth, board.canLink, board.color])).toEqual([
      ["M4 Child", 1, true, "#3B82F6"],
      ["M4 Existing", 1, true, "#3B82F6"],
      ["M4 Existing Kid", 2, true, "#3B82F6"],
    ]);
  });
});
