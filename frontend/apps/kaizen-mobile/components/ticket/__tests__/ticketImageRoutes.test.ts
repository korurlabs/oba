import { describe, expect, it } from "bun:test";

import { parseTicketImageTarget, ticketImageHref } from "@/components/ticket/ticketRoutes";

describe("ticketImageHref", () => {
  it("links a description image by its index", () => {
    expect(ticketImageHref("ALP-6", { index: 2 })).toEqual({
      pathname: "/ticket/[ticketKey]/image",
      params: { ticketKey: "ALP-6", index: "2" },
    });
  });

  it("links an attachment by its id", () => {
    expect(ticketImageHref("ALP-6", { attachmentId: "att-1" })).toEqual({
      pathname: "/ticket/[ticketKey]/image",
      params: { ticketKey: "ALP-6", attachmentId: "att-1" },
    });
  });
});

describe("parseTicketImageTarget", () => {
  it("reads back what ticketImageHref wrote", () => {
    expect(parseTicketImageTarget(ticketImageHref("ALP-6", { index: 0 }).params)).toEqual({
      index: 0,
    });
    expect(
      parseTicketImageTarget(ticketImageHref("ALP-6", { attachmentId: "att-1" }).params)
    ).toEqual({ attachmentId: "att-1" });
  });

  it("takes the first value of a repeated param", () => {
    expect(parseTicketImageTarget({ index: ["3", "4"] })).toEqual({ index: 3 });
  });

  it("prefers the attachment when a link carries both", () => {
    expect(parseTicketImageTarget({ index: "1", attachmentId: "att-1" })).toEqual({
      attachmentId: "att-1",
    });
  });

  it("refuses an index that is not a whole non-negative number, or no target at all", () => {
    expect(parseTicketImageTarget({ index: "-1" })).toBeNull();
    expect(parseTicketImageTarget({ index: "1.5" })).toBeNull();
    expect(parseTicketImageTarget({ index: "two" })).toBeNull();
    expect(parseTicketImageTarget({ index: "" })).toBeNull();
    expect(parseTicketImageTarget({ attachmentId: "" })).toBeNull();
    expect(parseTicketImageTarget({ ticketKey: "ALP-6" })).toBeNull();
  });
});
