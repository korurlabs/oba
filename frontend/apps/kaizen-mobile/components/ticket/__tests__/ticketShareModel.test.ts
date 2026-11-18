import { describe, expect, it } from "bun:test";

import { ticketShareContent } from "@/components/ticket/ticketShareModel";

const URL = "https://kaizen.example.com/en/tickets/ALP-6";

describe("ticketShareContent", () => {
  it("gives iOS the link as its own item next to the key and title", () => {
    expect(
      ticketShareContent({ key: "ALP-6", title: "Fix the login redirect" }, URL, "ios")
    ).toEqual({
      title: "ALP-6 Fix the login redirect",
      message: "ALP-6 Fix the login redirect",
      url: URL,
    });
  });

  it("puts the link under the text on Android, which shares only the message", () => {
    expect(
      ticketShareContent({ key: "ALP-6", title: "Fix the login redirect" }, URL, "android")
    ).toEqual({
      title: "ALP-6 Fix the login redirect",
      message: `ALP-6 Fix the login redirect\n${URL}`,
    });
  });

  it("shares the key alone when the title is missing or blank", () => {
    expect(ticketShareContent({ key: "ALP-6" }, URL, "ios").message).toBe("ALP-6");
    expect(ticketShareContent({ key: "ALP-6", title: "  " }, URL, "android").message).toBe(
      `ALP-6\n${URL}`
    );
  });
});
