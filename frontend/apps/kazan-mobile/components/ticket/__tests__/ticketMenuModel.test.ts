import { describe, expect, it } from "bun:test";

import { canWriteBoard } from "@/components/boards/boardModel";
import {
  ticketMenuItems,
  type TicketMenuGates,
  type TicketMenuHandlers,
  type TicketMenuItemId,
} from "@/components/ticket/ticketMenuModel";

const ignore = (): void => undefined;

/** Every handler, as the ticket screen of a ticket in a sprint and a held board card pass them. */
const ALL_HANDLERS: TicketMenuHandlers = {
  open: ignore,
  changeStatus: ignore,
  assignToMe: ignore,
  setPriority: ignore,
  moveToBoard: ignore,
  moveToSprint: ignore,
  moveToBacklog: ignore,
  copyLink: ignore,
  shareToChat: ignore,
  openWeb: ignore,
  delete: ignore,
};

const OPEN_GATES: Omit<TicketMenuGates, "canWrite"> = {
  canChangeStatus: true,
  isAssignee: false,
};

function ids(
  gates: TicketMenuGates,
  handlers: TicketMenuHandlers = ALL_HANDLERS
): TicketMenuItemId[] {
  return ticketMenuItems(gates, handlers).map((item) => item.id);
}

const READ_ONLY: TicketMenuItemId[] = ["open", "copyLink", "shareToChat", "openWeb"];
const WRITER: TicketMenuItemId[] = [
  "open",
  "status",
  "assignToMe",
  "priority",
  "moveBoard",
  "moveSprint",
  "moveBacklog",
  "copyLink",
  "shareToChat",
  "openWeb",
  "delete",
];

describe("ticketMenuItems per role", () => {
  const cases: [string, string | null, string | null, TicketMenuItemId[]][] = [
    ["organization guest on a board they own", "GUEST", "OWNER", READ_ONLY],
    ["board viewer", "MEMBER", "VIEWER", READ_ONLY],
    ["no board role yet", "MEMBER", null, READ_ONLY],
    ["board member", "MEMBER", "MEMBER", WRITER],
    ["board admin", "MEMBER", "ADMIN", WRITER],
    ["board owner", "OWNER", "OWNER", WRITER],
  ];
  for (const [name, organizationRole, boardRole, expected] of cases) {
    it(`${name} gets ${expected === READ_ONLY ? "only the reading actions" : "every action"}`, () => {
      const canWrite: boolean = canWriteBoard(organizationRole, boardRole);
      expect(ids({ canWrite, ...OPEN_GATES })).toEqual(expected);
    });
  }
});

describe("ticketMenuItems gates within write rights", () => {
  it("leaves out Change status for a top-level backlog ticket", () => {
    expect(ids({ canWrite: true, canChangeStatus: false, isAssignee: false })).not.toContain(
      "status"
    );
  });

  it("leaves out Assign to me for an assignee and while the user is unknown", () => {
    expect(ids({ canWrite: true, canChangeStatus: true, isAssignee: true })).not.toContain(
      "assignToMe"
    );
    expect(ids({ canWrite: true, canChangeStatus: true, isAssignee: null })).not.toContain(
      "assignToMe"
    );
  });

  it("leaves out the actions whose handler the caller does not pass", () => {
    const handlers: TicketMenuHandlers = {
      ...ALL_HANDLERS,
      open: undefined,
      moveToSprint: undefined,
      moveToBacklog: undefined,
      shareToChat: undefined,
      openWeb: undefined,
    };
    expect(ids({ canWrite: true, ...OPEN_GATES }, handlers)).toEqual([
      "status",
      "assignToMe",
      "priority",
      "moveBoard",
      "copyLink",
      "delete",
    ]);
  });

  it("leaves out Move to board for a sub-ticket, whose caller passes no handler", () => {
    const subTicket: TicketMenuHandlers = { ...ALL_HANDLERS, moveToBoard: undefined };
    const items: TicketMenuItemId[] = ids({ canWrite: true, ...OPEN_GATES }, subTicket);
    expect(items).not.toContain("moveBoard");
    expect(items).toContain("moveSprint");
    expect(items).toContain("moveBacklog");
  });

  it("marks only Delete as destructive, and it comes last", () => {
    const items = ticketMenuItems({ canWrite: true, ...OPEN_GATES }, ALL_HANDLERS);
    expect(items.filter((item) => item.destructive).map((item) => item.id)).toEqual(["delete"]);
    expect(items.at(-1)?.id).toBe("delete");
  });
});

describe("ticketMenuItems Select", () => {
  it("offers Select after Open when the caller passes its handler", () => {
    const items: TicketMenuItemId[] = ids(
      { canWrite: true, ...OPEN_GATES },
      { ...ALL_HANDLERS, select: ignore }
    );
    expect(items.slice(0, 2)).toEqual(["open", "select"]);
  });

  it("leaves Select to the caller's board role check, not to write rights", () => {
    expect(ids({ canWrite: false, ...OPEN_GATES }, { ...ALL_HANDLERS, select: ignore })).toEqual([
      "open",
      "select",
      ...READ_ONLY.slice(1),
    ]);
    expect(ids({ canWrite: true, ...OPEN_GATES })).not.toContain("select");
  });
});
