import { describe, expect, test } from "bun:test";
import { createApiResponseSchema } from "@oba/shared/schema";
import { z } from "zod";

import {
  boardMemberCandidateListSchema,
  boardMemberListSchema,
  boardOptionListSchema,
  milestoneDetailSchema,
  milestoneListSchema,
} from "@/lib/schemas/boards";
import { chatMemberResponseSchema } from "@/lib/schemas/chat";

/*
 * Bodies as the backend sends them, field by field from `LabelResponse`,
 * `ProjectListResponse`, `MilestoneDetailResponse`,
 * `BoardMemberCandidateResponse`, `BoardMemberResponse` and
 * `ChatMemberResponse`, inside the `CustomResponse` envelope with its null
 * `pagination` and `error`. A body without `kaizenAccess` is an API from
 * before the app access check.
 * `readData` of lib/api.ts does the same two parses; that module needs the
 * native runtime, so the envelope is checked here with the schema it uses.
 */

function readData<T extends z.ZodType>(dataSchema: T, body: unknown): z.infer<T> {
  return createApiResponseSchema(dataSchema).parse(body).data as z.infer<T>;
}

const ORG_BOARD = "b0000000-0000-0000-0000-000000000001";

function envelope(data: unknown, pagination: unknown = null): unknown {
  return { success: true, message: "OK", data, pagination, error: null };
}

describe("board settings responses", () => {
  test("reads labels and projects with their owning board, a related board's included", () => {
    const options = readData(
      boardOptionListSchema,
      envelope([
        {
          id: "c0000000-0000-0000-0000-000000000001",
          name: "Bug",
          color: "#EF4444",
          boardId: ORG_BOARD,
          boardName: "Kaizen",
        },
        {
          id: "c0000000-0000-0000-0000-000000000002",
          name: "Infra",
          color: null,
          boardId: "b0000000-0000-0000-0000-000000000002",
          boardName: "Platform",
        },
      ])
    );
    expect(options.map((option) => [option.boardId, option.boardName])).toEqual([
      [ORG_BOARD, "Kaizen"],
      ["b0000000-0000-0000-0000-000000000002", "Platform"],
    ]);
  });

  test("reads a milestone detail with null dates, colour and description", () => {
    const milestone = readData(
      milestoneDetailSchema,
      envelope({
        id: "d0000000-0000-0000-0000-000000000001",
        name: "Public beta",
        status: "OPEN",
        startDate: null,
        targetDate: "2026-12-15",
        boardId: ORG_BOARD,
        boardName: "Kaizen",
        boardPrefix: "KZN",
        doneCount: 0,
        totalCount: 0,
        health: "ON_TRACK",
        color: null,
        dependsOn: [],
        description: null,
        createdAt: "2026-11-10T09:00:00Z",
        updatedAt: "2026-11-10T09:00:00Z",
      })
    );
    expect(milestone.targetDate).toBe("2026-12-15");
    expect(milestone.description).toBeNull();
  });

  test("reads the member candidates, with and without a name and Kaizen", () => {
    const candidates = readData(
      boardMemberCandidateListSchema,
      envelope([
        {
          userId: "a0000000-0000-0000-0000-000000000001",
          email: "dev@example.com",
          firstName: "Dev",
          lastName: null,
          role: "OWNER",
          kaizenAccess: true,
        },
        {
          userId: "a0000000-0000-0000-0000-000000000002",
          email: "new@example.com",
          firstName: null,
          lastName: null,
          role: "MEMBER",
          kaizenAccess: false,
        },
        {
          userId: "a0000000-0000-0000-0000-000000000003",
          email: "old@example.com",
          firstName: "Old",
          lastName: "Api",
          role: "ADMIN",
        },
      ])
    );
    expect(candidates.map((candidate) => [candidate.firstName, candidate.kaizenAccess])).toEqual([
      ["Dev", true],
      [null, false],
      ["Old", undefined],
    ]);
  });

  test("reads a board member with and without kaizenAccess", () => {
    const member = {
      id: "e0000000-0000-0000-0000-000000000001",
      userId: "a0000000-0000-0000-0000-000000000002",
      email: "new@example.com",
      firstName: "New",
      lastName: "Member",
      role: "MEMBER",
      createdAt: "2026-01-01T00:00:00Z",
    };
    const members = readData(
      boardMemberListSchema,
      envelope([{ ...member, kaizenAccess: false }, member])
    );
    expect(members.map((item) => item.kaizenAccess)).toEqual([false, undefined]);
  });

  test("reads a chat member with and without kaizenAccess", () => {
    const member = {
      userId: "a0000000-0000-0000-0000-000000000002",
      firstName: "New",
      lastName: null,
      email: "new@example.com",
      role: "MEMBER",
      online: false,
    };
    const members = readData(
      z.array(chatMemberResponseSchema),
      envelope([{ ...member, kaizenAccess: false }, member])
    );
    expect(members.map((item) => item.kaizenAccess)).toEqual([false, undefined]);
  });
});

/*
 * Bodies captured from the local backend: `GET /kaizen/boards/{boardId}/labels`
 * and `/projects` of a seeded board, with the envelope exactly as it came.
 */
describe("board settings responses captured from the backend", () => {
  test("reads the label list of a board", () => {
    const labels = readData(boardOptionListSchema, {
      success: true,
      message: "Labels retrieved successfully",
      data: [
        {
          id: "784a73da-0c97-4368-8398-0707c26c2a70",
          name: "M2 fixture label",
          color: "#3B82F6",
          boardId: "6cda572b-16ec-4da8-bb41-510a208c7b1b",
          boardName: "Alpha",
        },
      ],
      pagination: null,
      error: null,
    });
    expect(labels).toEqual([
      {
        id: "784a73da-0c97-4368-8398-0707c26c2a70",
        name: "M2 fixture label",
        color: "#3B82F6",
        boardId: "6cda572b-16ec-4da8-bb41-510a208c7b1b",
        boardName: "Alpha",
      },
    ]);
  });

  test("reads the project list of a board", () => {
    const projects = readData(boardOptionListSchema, {
      success: true,
      message: "Projects retrieved successfully",
      data: [
        {
          id: "1d6b59ac-e5f4-45d4-aca9-2774bff5a663",
          name: "M2 fixture project",
          color: "#22C55E",
          boardId: "6cda572b-16ec-4da8-bb41-510a208c7b1b",
          boardName: "Alpha",
        },
      ],
      pagination: null,
      error: null,
    });
    expect(projects.map((project) => [project.name, project.boardName])).toEqual([
      ["M2 fixture project", "Alpha"],
    ]);
  });

  test("reads an empty list", () => {
    expect(
      readData(boardOptionListSchema, {
        success: true,
        message: "Labels retrieved successfully",
        data: [],
        pagination: null,
        error: null,
      })
    ).toEqual([]);
  });
});

describe("milestone responses captured from the backend", () => {
  const listItem = {
    id: "b9281808-52cb-4c0c-9674-f33d3ac73781",
    name: "M3 test milestone",
    status: "OPEN",
    startDate: "2026-11-16",
    targetDate: "2026-12-18",
    boardId: "6cda572b-16ec-4da8-bb41-510a208c7b1b",
    boardName: "Alpha",
    boardPrefix: "ALP",
    doneCount: 0,
    totalCount: 0,
    health: "ON_TRACK",
    color: "#C084FC",
    dependsOn: [],
  };

  test("reads the milestone list of a board", () => {
    const milestones = readData(milestoneListSchema, {
      success: true,
      message: "Milestones retrieved successfully",
      data: [listItem],
      pagination: null,
      error: null,
    });
    expect(milestones).toHaveLength(1);
    expect(milestones[0]).toMatchObject({
      id: listItem.id,
      name: "M3 test milestone",
      status: "OPEN",
      startDate: "2026-11-16",
      targetDate: "2026-12-18",
      doneCount: 0,
      totalCount: 0,
      color: "#C084FC",
    });
  });

  test("reads one milestone with its description", () => {
    const milestone = readData(milestoneDetailSchema, {
      success: true,
      message: "Milestone retrieved successfully",
      data: {
        ...listItem,
        description: "Fixture capture",
        createdAt: "2026-11-11T10:40:08.722006Z",
        updatedAt: "2026-11-11T10:40:08.722006Z",
      },
      pagination: null,
      error: null,
    });
    expect(milestone).toMatchObject({
      name: "M3 test milestone",
      description: "Fixture capture",
      boardId: "6cda572b-16ec-4da8-bb41-510a208c7b1b",
      boardName: "Alpha",
      boardPrefix: "ALP",
    });
  });
});
