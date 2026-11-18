import { describe, expect, it } from "bun:test";

import { canCreateBoard, canWriteBoard } from "@/components/boards/boardModel";
import {
  chatCanModerate,
  chatCanWrite,
  chatMessageActions,
} from "@/components/chat/chatMessageActions";
import { chatThreadMenuActions } from "@/components/chat/chatThreadModel";
import { canWriteTickets } from "@/components/home/homeModel";
import { canSeeNotPosted } from "@/components/updates/updateModel";
import { ME, chatMessage } from "@/components/chat/__tests__/chatFixtures";

const WRITING_ROLES: string[] = ["OWNER", "ADMIN", "MEMBER"];

describe("a GUEST of the organization", () => {
  it("cannot drag or swipe tickets, whatever the board role", () => {
    for (const boardRole of ["OWNER", "ADMIN", "MEMBER", "VIEWER", null]) {
      expect(canWriteBoard("GUEST", boardRole)).toBe(false);
    }
  });

  it("is offered no create action", () => {
    expect(canCreateBoard("GUEST")).toBe(false);
    expect(canWriteTickets("GUEST")).toBe(false);
    expect(chatCanModerate("GUEST")).toBe(false);
  });

  it("has no composer, no reaction and no thread to join", () => {
    expect(chatCanWrite("GUEST")).toBe(false);
    expect(
      chatThreadMenuActions({ status: "ACTIVE", member: false, canManage: false }, false)
    ).toEqual([]);
    const actions = chatMessageActions(chatMessage(), { meId: ME, role: "GUEST" });
    for (const hidden of ["reply", "react", "pin", "edit", "delete"] as const) {
      expect(actions).not.toContain(hidden);
    }
  });

  it("does not see Not posted or Nudge", () => {
    expect(canSeeNotPosted("GUEST")).toBe(false);
  });
});

describe("a MEMBER of the organization", () => {
  it("writes but does not see Not posted or Nudge", () => {
    expect(canSeeNotPosted("MEMBER")).toBe(false);
    expect(chatCanWrite("MEMBER")).toBe(true);
    expect(canWriteBoard("MEMBER", "MEMBER")).toBe(true);
  });

  it("cannot create channels", () => {
    expect(chatCanModerate("MEMBER")).toBe(false);
  });
});

describe("OWNER and ADMIN", () => {
  it("get every write action and Not posted", () => {
    for (const role of ["OWNER", "ADMIN"]) {
      expect(canSeeNotPosted(role)).toBe(true);
      expect(chatCanModerate(role)).toBe(true);
    }
    for (const role of WRITING_ROLES) {
      expect(canCreateBoard(role)).toBe(true);
      expect(canWriteTickets(role)).toBe(true);
      expect(chatCanWrite(role)).toBe(true);
    }
  });
});
