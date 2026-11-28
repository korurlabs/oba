import { describe, expect, test } from "bun:test";
import type { MailSenderListItem } from "../schemas";
import { filterMailSenders, hasActiveMailSenderFilter } from "./mail-sender-filters";

function sender(id: string, mailAddress: string): MailSenderListItem {
  return {
    id,
    mailAddress,
    type: "PERSONAL",
    addedBy: "2f6c1a9e-3b4d-4e5f-8a7b-9c0d1e2f3a4b",
    createdAt: "2026-11-12T09:00:00Z",
  };
}

const senders = [sender("1", "Jane.Doe@example.com"), sender("2", "sales@example.com")];

describe("filterMailSenders", () => {
  test("no search keeps every sender", () => {
    expect(filterMailSenders(senders, { search: null })).toEqual(senders);
  });

  test("a blank search keeps every sender", () => {
    expect(filterMailSenders(senders, { search: "   " })).toEqual(senders);
  });

  test("the search matches part of the mail address, ignoring case and spaces", () => {
    expect(filterMailSenders(senders, { search: " jane.DOE " }).map((s) => s.id)).toEqual(["1"]);
  });

  test("a search that matches nothing leaves no sender", () => {
    expect(filterMailSenders(senders, { search: "billing" })).toEqual([]);
  });
});

describe("hasActiveMailSenderFilter", () => {
  test("only a non-blank search narrows the list", () => {
    expect(hasActiveMailSenderFilter({ search: null })).toBe(false);
    expect(hasActiveMailSenderFilter({ search: "  " })).toBe(false);
    expect(hasActiveMailSenderFilter({ search: "jane" })).toBe(true);
  });
});
