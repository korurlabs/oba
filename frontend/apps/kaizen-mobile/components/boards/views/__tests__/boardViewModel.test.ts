import { describe, expect, it } from "bun:test";

import { navBarNewTicket } from "@/components/boards/views/boardViewModel";

describe("navBarNewTicket", () => {
  it("adds to the sprint shown on Sprint where no floating button does", () => {
    expect(navBarNewTicket("sprint", "s1", false)).toBe("s1");
  });

  it("leaves Sprint to its floating button on Android", () => {
    expect(navBarNewTicket("sprint", "s1", true)).toBeUndefined();
  });

  it("has nothing to add to on Sprint without a sprint", () => {
    expect(navBarNewTicket("sprint", null, false)).toBeUndefined();
  });

  it("adds to the backlog on All and Backlog, on both platforms", () => {
    expect(navBarNewTicket("all", "s1", false)).toBeNull();
    expect(navBarNewTicket("backlog", "s1", true)).toBeNull();
  });

  it("is not offered on Roadmap", () => {
    expect(navBarNewTicket("roadmap", "s1", false)).toBeUndefined();
  });
});
