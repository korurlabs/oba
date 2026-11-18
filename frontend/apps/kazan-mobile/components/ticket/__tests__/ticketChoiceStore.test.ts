import { describe, expect, it } from "bun:test";

import {
  clearTicketChoice,
  showTicketChoice,
  whenTicketChoiceClosed,
} from "@/components/ticket/ticketChoiceStore";
import type { ChoicePicker } from "@/components/ticket/ticketPickers";

const PICKER: ChoicePicker = {
  title: "Status",
  kind: "status",
  current: "IN_REVIEW",
  options: [],
  onPick: () => undefined,
};

describe("whenTicketChoiceClosed", () => {
  it("runs at once when no choice sheet is open", () => {
    let runs = 0;
    whenTicketChoiceClosed(() => {
      runs += 1;
    });
    expect(runs).toBe(1);
  });

  it("waits for the open choice sheet to go, then runs once", () => {
    let runs = 0;
    showTicketChoice(PICKER);
    whenTicketChoiceClosed(() => {
      runs += 1;
    });
    expect(runs).toBe(0);

    clearTicketChoice(PICKER);
    expect(runs).toBe(1);

    showTicketChoice(PICKER);
    clearTicketChoice(PICKER);
    expect(runs).toBe(1);
  });
});
