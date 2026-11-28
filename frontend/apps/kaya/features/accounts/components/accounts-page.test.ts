import { describe, expect, test } from "bun:test";

import type { AccountListResponse } from "../schemas";
import { searchAccounts } from "./accounts-page";

function account(id: string, email: string, organization: string | null, users: string[]) {
  return {
    id,
    accountEmail: email,
    accountOrganizationName: organization,
    users: users.map((displayName: string, index: number) => ({
      userId: `${id}-${index}`,
      displayName,
    })),
  } as unknown as AccountListResponse;
}

const ACCOUNTS = [
  account("a", "ada@korur.com", "Korur", ["Ada Lovelace"]),
  account("b", "bob@example.com", null, ["Bob"]),
];

describe("searchAccounts", () => {
  test("an empty search keeps every account", () => {
    expect(searchAccounts(ACCOUNTS, "  ").map((entry) => entry.id)).toEqual(["a", "b"]);
  });

  test("matches the e-mail, the organization or a user, ignoring case", () => {
    expect(searchAccounts(ACCOUNTS, "EXAMPLE").map((entry) => entry.id)).toEqual(["b"]);
    expect(searchAccounts(ACCOUNTS, "korur").map((entry) => entry.id)).toEqual(["a"]);
    expect(searchAccounts(ACCOUNTS, "personal").map((entry) => entry.id)).toEqual(["b"]);
    expect(searchAccounts(ACCOUNTS, "lovelace").map((entry) => entry.id)).toEqual(["a"]);
  });
});
