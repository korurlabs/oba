import { describe, expect, it } from "bun:test";

import { boardHref } from "@/components/boards/boardRoutes";
import { ticketExitHref } from "@/components/ticket/ticketRoutes";

describe("ticketExitHref", () => {
  it("leaves to the ticket's board when it is known", () => {
    expect(ticketExitHref("board-1")).toEqual(boardHref("board-1"));
  });

  it("leaves to the Boards tab while the board is not known", () => {
    expect(ticketExitHref(undefined)).toBe("/boards");
    expect(ticketExitHref(null)).toBe("/boards");
    expect(ticketExitHref("")).toBe("/boards");
  });
});
