import { describe, expect, it } from "bun:test";

import {
  chatChannelAddCandidates,
  chatChannelMemberRows,
  chatMemberPickBlocked,
  chatMemberPickSubtitle,
  chatMemberSections,
  chatOnlineCount,
  chatThreadMemberRows,
  type ChatMemberRow,
} from "@/components/chat/chatMembersModel";
import type { ChatMemberResponse, ChatUserResponse } from "@/lib/schemas/chat";

import { ALICE, ME } from "./chatFixtures";

const BOB = "44444444-4444-4444-8444-444444444444";

function member(overrides: Partial<ChatMemberResponse>): ChatMemberResponse {
  return {
    userId: ALICE,
    firstName: "Alice",
    lastName: "Akın",
    email: "alice@example.com",
    role: "MEMBER",
    online: false,
    ...overrides,
  };
}

function user(overrides: Partial<ChatUserResponse>): ChatUserResponse {
  return {
    userId: ALICE,
    firstName: "Alice",
    lastName: "Akın",
    email: "alice@example.com",
    member: true,
    ...overrides,
  };
}

describe("chatMemberSections", () => {
  it("puts online members first and sorts each section by name, ignoring case", () => {
    const rows: ChatMemberRow[] = [
      { userId: "1", name: "zeynep", online: true },
      { userId: "2", name: "Can", online: false },
      { userId: "3", name: "Ayşe", online: true },
      { userId: "4", name: "bora", online: false },
    ];
    const sections = chatMemberSections(rows);
    expect(sections.online.map((row) => row.name)).toEqual(["Ayşe", "zeynep"]);
    expect(sections.offline.map((row) => row.name)).toEqual(["bora", "Can"]);
  });

  it("gives an empty online section when no one is online", () => {
    const sections = chatMemberSections([{ userId: "1", name: "Can", online: false }]);
    expect(sections.online).toEqual([]);
    expect(sections.offline).toHaveLength(1);
  });

  it("does not reorder the rows it was given", () => {
    const rows: ChatMemberRow[] = [
      { userId: "1", name: "b", online: true },
      { userId: "2", name: "a", online: true },
    ];
    chatMemberSections(rows);
    expect(rows.map((row) => row.userId)).toEqual(["1", "2"]);
  });
});

describe("chatChannelMemberRows", () => {
  it("names a member by first and last name, else email, else the unknown label", () => {
    const rows = chatChannelMemberRows(
      [
        member({ userId: ALICE, online: true }),
        member({ userId: BOB, firstName: null, lastName: null, email: "bob@example.com" }),
        member({ userId: ME, firstName: null, lastName: null, email: null }),
      ],
      "Unknown member"
    );
    expect(rows).toEqual([
      { userId: ALICE, name: "Alice Akın", online: true },
      { userId: BOB, name: "bob@example.com", online: false },
      { userId: ME, name: "Unknown member", online: false },
    ]);
  });
});

describe("chatThreadMemberRows", () => {
  it("takes presence from the organization's members", () => {
    const rows = chatThreadMemberRows(
      [user({ userId: ALICE }), user({ userId: BOB, firstName: "Bob", lastName: "Bal" })],
      [member({ userId: ALICE, online: true }), member({ userId: BOB, online: false })],
      "Unknown member"
    );
    expect(rows).toEqual([
      { userId: ALICE, name: "Alice Akın", online: true },
      { userId: BOB, name: "Bob Bal", online: false },
    ]);
  });

  it("shows everyone offline while the organization's members are not loaded", () => {
    const rows = chatThreadMemberRows([user({ userId: ALICE })], undefined, "Unknown member");
    expect(rows).toEqual([{ userId: ALICE, name: "Alice Akın", online: false }]);
  });

  it("leaves out someone who has left the organization", () => {
    const rows = chatThreadMemberRows(
      [user({ userId: ALICE }), user({ userId: BOB, member: false })],
      [member({ userId: ALICE, online: true })],
      "Unknown member"
    );
    expect(rows.map((row) => row.userId)).toEqual([ALICE]);
  });
});

describe("chatOnlineCount", () => {
  it("counts the online members and knows nothing before they load", () => {
    expect(chatOnlineCount(undefined)).toBeNull();
    expect(chatOnlineCount([])).toBe(0);
    expect(
      chatOnlineCount([
        member({ online: true }),
        member({ userId: BOB }),
        member({ userId: ME, online: true }),
      ])
    ).toBe(2);
  });
});

describe("chatChannelAddCandidates", () => {
  const organization: ChatMemberResponse[] = [
    member({ userId: ME, firstName: "Me" }),
    member({ userId: ALICE }),
    member({ userId: BOB, firstName: "Bob" }),
  ];

  it("offers the organization's members who are not on the list, in its order", () => {
    const candidates = chatChannelAddCandidates(organization, [member({ userId: ALICE })]);
    expect(candidates.map((candidate) => candidate.userId)).toEqual([ME, BOB]);
  });

  it("offers everyone while the list is unknown, the reader included", () => {
    expect(chatChannelAddCandidates(organization, undefined).map((m) => m.userId)).toEqual([
      ME,
      ALICE,
      BOB,
    ]);
  });

  it("offers nobody before the organization's members load", () => {
    expect(chatChannelAddCandidates(undefined, [])).toEqual([]);
  });

  it("keeps a member without Kazan with the flag, so the sheet can show why", () => {
    const candidates = chatChannelAddCandidates(
      [member({ userId: ALICE, kazanAccess: true }), member({ userId: BOB, kazanAccess: false })],
      []
    );
    expect(candidates.map((candidate) => [candidate.userId, candidate.kazanAccess])).toEqual([
      [ALICE, true],
      [BOB, false],
    ]);
  });
});

describe("chatMemberPickBlocked", () => {
  it("blocks a member without Kazan who is not picked", () => {
    expect(chatMemberPickBlocked({ kazanAccess: false }, false)).toBe(true);
  });

  it("blocks a member whose response carries no flag", () => {
    expect(chatMemberPickBlocked({}, false)).toBe(true);
  });

  it("keeps a picked member without Kazan pickable, so they can be removed", () => {
    expect(chatMemberPickBlocked({ kazanAccess: false }, true)).toBe(false);
  });

  it("never blocks a member with Kazan", () => {
    expect(chatMemberPickBlocked({ kazanAccess: true }, false)).toBe(false);
  });
});

describe("chatMemberPickSubtitle", () => {
  it("shows the email of a pickable row", () => {
    expect(chatMemberPickSubtitle("alice@example.com", false, "No Kazan access")).toBe(
      "alice@example.com"
    );
  });

  it("puts the reason under the email of a blocked row", () => {
    expect(chatMemberPickSubtitle("alice@example.com", true, "No Kazan access")).toBe(
      "alice@example.com\nNo Kazan access"
    );
  });

  it("shows the reason alone without an email, and nothing for a pickable row", () => {
    expect(chatMemberPickSubtitle(null, true, "No Kazan access")).toBe("No Kazan access");
    expect(chatMemberPickSubtitle(null, false, "No Kazan access")).toBeUndefined();
  });
});
