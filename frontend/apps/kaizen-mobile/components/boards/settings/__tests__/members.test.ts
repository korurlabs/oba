import { describe, expect, it } from "bun:test";
import { createApiResponseSchema } from "@oba/shared/schema";
import { z } from "zod";

import {
  candidateName,
  memberActions,
  memberCandidates,
} from "@/components/boards/settings/boardSettingsModel";
import {
  boardMemberCandidateListSchema,
  boardMemberListSchema,
  boardMemberSchema,
  type BoardMember,
  type BoardMemberCandidate,
} from "@/lib/schemas/boards";

/*
 * Bodies captured from the local API as `dev` (the owner) on a throwaway
 * board: `GET /members/candidates`, the board's members after `POST /members`
 * added the other user as MEMBER, that answer, and the refusal of adding them
 * twice.
 * `readData` mirrors lib/api.ts, which needs the native runtime: the envelope
 * first, then its `data`, so a refusal's null `data` fails the data schema.
 */

const envelopeSchema = createApiResponseSchema(z.unknown());

function readData<T extends z.ZodType>(dataSchema: T, body: unknown): z.infer<T> {
  return dataSchema.parse(envelopeSchema.parse(body).data);
}

const CANDIDATES_BODY: unknown = {
  success: true,
  message: "Board member candidates retrieved successfully",
  data: [
    {
      userId: "da554751-f027-495c-8019-aa3898742ec3",
      email: "dev@example.com",
      firstName: "Dev",
      lastName: "Owner",
      role: "OWNER",
      kaizenAccess: true,
    },
    {
      userId: "3e0c2e3b-55dd-4f55-ade3-01a1011272d1",
      email: "admin@example.com",
      firstName: "Sensey",
      lastName: "Admin",
      role: "MEMBER",
      kaizenAccess: true,
    },
  ],
  pagination: null,
  error: null,
};

const MEMBERS_BODY: unknown = {
  success: true,
  message: "Board members retrieved successfully",
  data: [
    {
      id: "204685b6-e044-4cf7-89bc-9a5e22ac5cf1",
      userId: "da554751-f027-495c-8019-aa3898742ec3",
      email: "dev@example.com",
      firstName: "Dev",
      lastName: "Owner",
      role: "OWNER",
      createdAt: "2026-11-11T11:14:06.050551Z",
    },
    {
      id: "a433183a-58d0-4c66-872d-fbec5793fb33",
      userId: "3e0c2e3b-55dd-4f55-ade3-01a1011272d1",
      email: "admin@example.com",
      firstName: "Sensey",
      lastName: "Admin",
      role: "MEMBER",
      createdAt: "2026-11-11T11:14:06.086346Z",
    },
  ],
  pagination: null,
  error: null,
};

const ADD_MEMBER_BODY: unknown = {
  success: true,
  message: "Board member added successfully",
  data: {
    id: "a433183a-58d0-4c66-872d-fbec5793fb33",
    userId: "3e0c2e3b-55dd-4f55-ade3-01a1011272d1",
    email: "admin@example.com",
    firstName: "Sensey",
    lastName: "Admin",
    role: "MEMBER",
    createdAt: "2026-11-11T11:14:06.086346Z",
  },
  pagination: null,
  error: null,
};

const ADD_MEMBER_AGAIN_BODY: unknown = {
  success: false,
  message: "User is already a member of this board.",
  data: null,
  pagination: null,
  error: {
    code: "KAI_STA_003",
    message: "User is already a member of this board.",
    errorValues: {
      userId: "3e0c2e3b-55dd-4f55-ade3-01a1011272d1",
      boardId: "353270b3-4604-475e-b831-c2484739a997",
    },
  },
};

const candidates: BoardMemberCandidate[] = readData(
  boardMemberCandidateListSchema,
  CANDIDATES_BODY
);
const members: BoardMember[] = readData(boardMemberListSchema, MEMBERS_BODY);

describe("member bodies of the local API", () => {
  it("parse with the app schemas", () => {
    expect(candidates.map((candidate) => [candidate.email, candidate.kaizenAccess])).toEqual([
      ["dev@example.com", true],
      ["admin@example.com", true],
    ]);
    expect(members.map((member) => [member.email, member.role])).toEqual([
      ["dev@example.com", "OWNER"],
      ["admin@example.com", "MEMBER"],
    ]);
    expect(readData(boardMemberSchema, ADD_MEMBER_BODY).role).toBe("MEMBER");
  });

  it("names a candidate by first and last name", () => {
    expect(candidates.map(candidateName)).toEqual(["Dev Owner", "Sensey Admin"]);
  });

  it("offers no one once every candidate is on the board", () => {
    expect(memberCandidates(candidates, members, "")).toEqual([]);
    expect(
      memberCandidates(candidates, members.slice(0, 1), "").map((candidate) => candidate.email)
    ).toEqual(["admin@example.com"]);
  });

  it("gives a manager role actions on the added member, never on the owner", () => {
    expect(memberActions(members[0]!, true)).toBeNull();
    expect(memberActions(members[1]!, true)?.roles).toEqual([
      { role: "ADMIN", selected: false },
      { role: "MEMBER", selected: true },
      { role: "VIEWER", selected: false },
    ]);
  });

  it("carries the server's message on a refused add", () => {
    expect(() => readData(boardMemberSchema, ADD_MEMBER_AGAIN_BODY)).toThrow();
    expect(
      (ADD_MEMBER_AGAIN_BODY as { error: { code: string; message: string } }).error
    ).toMatchObject({
      code: "KAI_STA_003",
      message: "User is already a member of this board.",
    });
  });
});
