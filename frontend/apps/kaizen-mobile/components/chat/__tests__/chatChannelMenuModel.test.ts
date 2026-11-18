import { describe, expect, it } from "bun:test";

import {
  chatChannelAuthorIds,
  chatChannelContentHidden,
  chatChannelDeleteMessageKey,
  chatChannelErrorKey,
  chatChannelGoneExpected,
  chatChannelManageActions,
  chatChannelMakesPrivate,
  chatChannelMakesPublic,
  chatChannelMembersOverLimit,
  chatChannelMenuActions,
  chatChannelUpdateChanged,
  chatChannelUpdateDraft,
  chatChannelUpdateRequest,
  chatChannelUpdateValid,
  type ChatChannelDraft,
  type ChatChannelMenuActionId,
  type ChatChannelRemovalState,
} from "@/components/chat/chatChannelMenuModel";
import {
  CHAT_CHANNEL_MAX_MEMBER_IDS,
  CHAT_CHANNEL_NAME_MAX,
  ChatChannelPurpose,
  ChatChannelVisibility,
} from "@/lib/schemas/chat";

import { ALICE, CHANNEL_ID, ME, chatChannel } from "./chatFixtures";

const ROLES: readonly (string | null)[] = ["OWNER", "ADMIN", "MEMBER", "GUEST", null];
const VISIBILITIES: readonly ChatChannelVisibility[] = [
  ChatChannelVisibility.PUBLIC,
  ChatChannelVisibility.PRIVATE,
];

/** The menu each case expects, written out the way the web app's channel menu lists it. */
function expectedMenu(
  role: string | null,
  visibility: ChatChannelVisibility,
  member: boolean
): ChatChannelMenuActionId[] {
  const manager: boolean = role === "OWNER" || role === "ADMIN";
  const isPrivate: boolean = visibility === ChatChannelVisibility.PRIVATE;
  const ids: ChatChannelMenuActionId[] = [];
  if (member) ids.push("members", "pins");
  if (manager) ids.push("create", "change");
  if (manager && isPrivate) ids.push("manageMembers");
  if (isPrivate && member) ids.push("leave");
  if (manager) ids.push("delete");
  return ids;
}

describe("chatChannelMenuActions", () => {
  for (const role of ROLES) {
    for (const visibility of VISIBILITIES) {
      for (const member of [true, false]) {
        it(`${role ?? "loading"} on a ${visibility.toLowerCase()} channel, member ${member}`, () => {
          expect(chatChannelMenuActions(chatChannel({ visibility, member }), role)).toEqual(
            expectedMenu(role, visibility, member)
          );
        });
      }
    }
  }

  it("offers New channel only to owners and admins", () => {
    for (const role of ROLES) {
      for (const visibility of VISIBILITIES) {
        const actions = chatChannelMenuActions(chatChannel({ visibility }), role);
        expect(actions.includes("create")).toBe(role === "OWNER" || role === "ADMIN");
      }
    }
  });

  it("lists an admin's private channel in the web menu's order, Delete last", () => {
    expect(
      chatChannelMenuActions(chatChannel({ visibility: ChatChannelVisibility.PRIVATE }), "ADMIN")
    ).toEqual(["members", "pins", "create", "change", "manageMembers", "leave", "delete"]);
  });

  it("gives a member of a private channel Members, Pinned messages and Leave", () => {
    expect(
      chatChannelMenuActions(chatChannel({ visibility: ChatChannelVisibility.PRIVATE }), "MEMBER")
    ).toEqual(["members", "pins", "leave"]);
  });
});

describe("chatChannelManageActions", () => {
  const purposes: readonly ChatChannelPurpose[] = [
    ChatChannelPurpose.DAILY_UPDATES,
    ChatChannelPurpose.PULL_REQUESTS,
  ];

  it("gives owners and admins New, Change and Delete on a purpose channel, in the web's order", () => {
    for (const purpose of purposes) {
      for (const role of ["OWNER", "ADMIN"]) {
        expect(chatChannelManageActions(chatChannel({ purpose }), role)).toEqual([
          "create",
          "change",
          "delete",
        ]);
      }
    }
  });

  it("gives members, guests and a loading role nothing to manage on a public channel", () => {
    for (const role of ["MEMBER", "GUEST", null]) {
      expect(chatChannelManageActions(chatChannel(), role)).toEqual([]);
    }
  });

  it("is the channel menu without Members and Pinned, for every role and visibility", () => {
    for (const role of ROLES) {
      for (const visibility of VISIBILITIES) {
        for (const member of [true, false]) {
          expect(chatChannelManageActions(chatChannel({ visibility, member }), role)).toEqual(
            expectedMenu(role, visibility, member).filter((id) => id !== "members" && id !== "pins")
          );
        }
      }
    }
  });
});

describe("chatChannelGoneExpected", () => {
  const idle: ChatChannelRemovalState = { removingId: null, leaving: false };

  it("expects no loss while nothing is being deleted or left", () => {
    expect(chatChannelGoneExpected(idle, CHANNEL_ID)).toBe(false);
  });

  it("expects the shown channel's loss from the moment its delete or leave is sent", () => {
    expect(chatChannelGoneExpected({ removingId: CHANNEL_ID, leaving: false }, CHANNEL_ID)).toBe(
      true
    );
  });

  it("does not expect the loss of a channel other than the one being removed", () => {
    expect(chatChannelGoneExpected({ removingId: "other", leaving: false }, CHANNEL_ID)).toBe(
      false
    );
  });

  it("keeps expecting it once the screen is closing", () => {
    expect(chatChannelGoneExpected({ removingId: null, leaving: true }, CHANNEL_ID)).toBe(true);
  });
});

describe("chatChannelContentHidden", () => {
  const sending: ChatChannelRemovalState = { removingId: CHANNEL_ID, leaving: false };
  const idle: ChatChannelRemovalState = { removingId: null, leaving: false };

  it("keeps the channel on screen while the request is on the way and the list still has it", () => {
    expect(chatChannelContentHidden(sending, CHANNEL_ID, chatChannel())).toBe(false);
  });

  it("hides the not-found state when a refresh drops the channel before the delete answers", () => {
    expect(chatChannelContentHidden(sending, CHANNEL_ID, undefined)).toBe(true);
  });

  it("hides the not-member state when a refresh shows the leave before it answers", () => {
    expect(chatChannelContentHidden(sending, CHANNEL_ID, chatChannel({ member: false }))).toBe(
      true
    );
  });

  it("shows a loss the reader did not cause, and one after a refused request", () => {
    expect(chatChannelContentHidden(idle, CHANNEL_ID, undefined)).toBe(false);
    expect(chatChannelContentHidden(idle, CHANNEL_ID, chatChannel({ member: false }))).toBe(false);
  });

  it("shows the loss of another channel while one is being removed", () => {
    expect(
      chatChannelContentHidden({ removingId: "other", leaving: false }, CHANNEL_ID, undefined)
    ).toBe(false);
  });

  it("hides everything once the screen is closing", () => {
    expect(
      chatChannelContentHidden({ removingId: null, leaving: true }, CHANNEL_ID, chatChannel())
    ).toBe(true);
  });
});

describe("chatChannelDeleteMessageKey", () => {
  it("says which posts stop reaching the chat, per purpose", () => {
    expect(chatChannelDeleteMessageKey(ChatChannelPurpose.GENERAL)).toBe("chatChannelDeleteBody");
    expect(chatChannelDeleteMessageKey(ChatChannelPurpose.DAILY_UPDATES)).toBe(
      "chatChannelDeleteBodyDailyUpdates"
    );
    expect(chatChannelDeleteMessageKey(ChatChannelPurpose.PULL_REQUESTS)).toBe(
      "chatChannelDeleteBodyPullRequests"
    );
  });
});

function draft(overrides: Partial<ChatChannelDraft> = {}): ChatChannelDraft {
  return {
    name: "release",
    visibility: ChatChannelVisibility.PUBLIC,
    purpose: ChatChannelPurpose.GENERAL,
    memberIds: [],
    ...overrides,
  };
}

describe("chatChannelUpdateRequest", () => {
  const publicChannel = chatChannel({ visibility: ChatChannelVisibility.PUBLIC });
  const privateChannel = chatChannel({ visibility: ChatChannelVisibility.PRIVATE });

  it("sends the picked members when a public channel becomes private", () => {
    expect(
      chatChannelUpdateRequest(
        publicChannel,
        draft({ visibility: ChatChannelVisibility.PRIVATE, memberIds: [ALICE] })
      )
    ).toEqual({
      name: "release",
      visibility: ChatChannelVisibility.PRIVATE,
      purpose: ChatChannelPurpose.GENERAL,
      memberIds: [ALICE],
    });
  });

  it("sends an empty member list when a public channel becomes private with nobody picked", () => {
    expect(
      chatChannelUpdateRequest(publicChannel, draft({ visibility: ChatChannelVisibility.PRIVATE }))
        .memberIds
    ).toEqual([]);
  });

  it("never sends members when a private channel stays private", () => {
    const request = chatChannelUpdateRequest(
      privateChannel,
      draft({ visibility: ChatChannelVisibility.PRIVATE, memberIds: [ALICE] })
    );
    expect(request.memberIds).toBeUndefined();
    expect("memberIds" in request && request.memberIds !== undefined).toBe(false);
  });

  it("sends no members when a channel stays public or becomes public", () => {
    expect(
      chatChannelUpdateRequest(publicChannel, draft({ memberIds: [ALICE] })).memberIds
    ).toBeUndefined();
    expect(
      chatChannelUpdateRequest(privateChannel, draft({ memberIds: [ALICE] })).memberIds
    ).toBeUndefined();
  });

  it("trims the name", () => {
    expect(chatChannelUpdateRequest(publicChannel, draft({ name: "  release  " })).name).toBe(
      "release"
    );
  });
});

describe("chatChannelUpdateDraft", () => {
  it("starts from the channel's values with no members picked", () => {
    expect(
      chatChannelUpdateDraft(
        chatChannel({
          name: "updates",
          visibility: ChatChannelVisibility.PUBLIC,
          purpose: ChatChannelPurpose.DAILY_UPDATES,
        })
      )
    ).toEqual({
      name: "updates",
      visibility: ChatChannelVisibility.PUBLIC,
      purpose: ChatChannelPurpose.DAILY_UPDATES,
      memberIds: [],
    });
  });
});

describe("visibility changes", () => {
  it("knows which way a channel's visibility changes", () => {
    const pub = chatChannel({ visibility: ChatChannelVisibility.PUBLIC });
    const priv = chatChannel({ visibility: ChatChannelVisibility.PRIVATE });
    expect(chatChannelMakesPrivate(pub, { visibility: ChatChannelVisibility.PRIVATE })).toBe(true);
    expect(chatChannelMakesPrivate(priv, { visibility: ChatChannelVisibility.PRIVATE })).toBe(
      false
    );
    expect(chatChannelMakesPublic(priv, { visibility: ChatChannelVisibility.PUBLIC })).toBe(true);
    expect(chatChannelMakesPublic(pub, { visibility: ChatChannelVisibility.PUBLIC })).toBe(false);
  });

  it("preselects every author except the reader", () => {
    expect(
      chatChannelAuthorIds(
        [
          { userId: ME, kaizenAccess: true },
          { userId: ALICE, kaizenAccess: true },
        ],
        ME
      )
    ).toEqual([ALICE]);
  });

  it("skips an author without Kaizen, whom the server refuses as a member", () => {
    const bob = "44444444-4444-4444-8444-444444444444";
    const carol = "55555555-5555-4555-8555-555555555555";
    expect(
      chatChannelAuthorIds(
        [
          { userId: ALICE, kaizenAccess: true },
          { userId: bob, kaizenAccess: false },
          { userId: carol },
        ],
        ME
      )
    ).toEqual([ALICE]);
  });
});

describe("chatChannelUpdateValid", () => {
  const publicChannel = chatChannel({ visibility: ChatChannelVisibility.PUBLIC });
  const privateChannel = chatChannel({ visibility: ChatChannelVisibility.PRIVATE });
  const tooMany: string[] = Array.from(
    { length: CHAT_CHANNEL_MAX_MEMBER_IDS + 1 },
    (_, i) => `u${i}`
  );

  it("needs a name of 1 to 80 characters after trimming", () => {
    expect(chatChannelUpdateValid(publicChannel, draft({ name: "   " }))).toBe(false);
    expect(
      chatChannelUpdateValid(publicChannel, draft({ name: "a".repeat(CHAT_CHANNEL_NAME_MAX) }))
    ).toBe(true);
    expect(
      chatChannelUpdateValid(publicChannel, draft({ name: "a".repeat(CHAT_CHANNEL_NAME_MAX + 1) }))
    ).toBe(false);
  });

  it("refuses a private channel with a purpose other than General", () => {
    expect(
      chatChannelUpdateValid(
        publicChannel,
        draft({
          visibility: ChatChannelVisibility.PRIVATE,
          purpose: ChatChannelPurpose.DAILY_UPDATES,
        })
      )
    ).toBe(false);
    expect(
      chatChannelUpdateValid(publicChannel, draft({ purpose: ChatChannelPurpose.DAILY_UPDATES }))
    ).toBe(true);
  });

  it("takes at most the members one request carries when the channel becomes private", () => {
    const makesPrivate = (memberIds: string[]): ChatChannelDraft =>
      draft({ visibility: ChatChannelVisibility.PRIVATE, memberIds });
    expect(chatChannelUpdateValid(publicChannel, makesPrivate(tooMany))).toBe(false);
    expect(chatChannelUpdateValid(publicChannel, makesPrivate(tooMany.slice(1)))).toBe(true);
  });

  it("ignores members that would not be sent", () => {
    // Public again after 201 authors were preselected: the update carries no members.
    expect(chatChannelUpdateValid(publicChannel, draft({ memberIds: tooMany }))).toBe(true);
    // Already private: members are managed on their own, the update carries none.
    expect(
      chatChannelUpdateValid(
        privateChannel,
        draft({ visibility: ChatChannelVisibility.PRIVATE, memberIds: tooMany })
      )
    ).toBe(true);
  });
});

describe("chatChannelMembersOverLimit", () => {
  it("is true above the members one request carries", () => {
    const ids: string[] = Array.from({ length: CHAT_CHANNEL_MAX_MEMBER_IDS }, (_, i) => `u${i}`);
    expect(chatChannelMembersOverLimit(ids)).toBe(false);
    expect(chatChannelMembersOverLimit([...ids, "one-more"])).toBe(true);
  });
});

describe("chatChannelUpdateChanged", () => {
  const publicChannel = chatChannel({ visibility: ChatChannelVisibility.PUBLIC });
  const initial: ChatChannelDraft = draft();

  it("is false for the values the sheet opened with", () => {
    expect(chatChannelUpdateChanged(publicChannel, initial, draft())).toBe(false);
    expect(chatChannelUpdateChanged(publicChannel, initial, draft({ name: " release " }))).toBe(
      false
    );
  });

  it("is true for a changed name, visibility or purpose", () => {
    expect(chatChannelUpdateChanged(publicChannel, initial, draft({ name: "rel" }))).toBe(true);
    expect(
      chatChannelUpdateChanged(
        publicChannel,
        initial,
        draft({ visibility: ChatChannelVisibility.PRIVATE })
      )
    ).toBe(true);
    expect(
      chatChannelUpdateChanged(
        publicChannel,
        initial,
        draft({ purpose: ChatChannelPurpose.PULL_REQUESTS })
      )
    ).toBe(true);
  });

  it("is false when the draft went back to public with the authors still picked", () => {
    expect(chatChannelUpdateChanged(publicChannel, initial, draft({ memberIds: [ALICE] }))).toBe(
      false
    );
  });
});

describe("chatChannelErrorKey", () => {
  it("names the web app's text for each refusal it explains", () => {
    expect(chatChannelErrorKey("KAI_VAL_051")).toBe("chatChannelErrorMembersNotExpected");
    expect(chatChannelErrorKey("KAI_VAL_052")).toBe("chatChannelErrorNotPrivate");
    expect(chatChannelErrorKey("KAI_VAL_053")).toBe("chatChannelErrorPurposePrivate");
    expect(chatChannelErrorKey("KAI_STA_016")).toBe("chatChannelErrorPurposeTaken");
    expect(chatChannelErrorKey("KAI_VAL_044")).toBe("chatChannelErrorMemberNotActive");
    expect(chatChannelErrorKey("KAI_NOT_015")).toBe("chatChannelErrorNotFound");
    expect(chatChannelErrorKey("KAI_SEC_011")).toBe("chatChannelErrorNotAllowed");
    expect(chatChannelErrorKey("ORG_SEC_003")).toBe("chatChannelErrorNotAllowed");
    expect(chatChannelErrorKey("APP_VAL_002")).toBe("chatChannelErrorNoKaizenAccess");
  });

  it("leaves any other code to the server's message", () => {
    expect(chatChannelErrorKey("KAI_STA_017")).toBeNull();
    expect(chatChannelErrorKey("GEN_VAL_001")).toBeNull();
    expect(chatChannelErrorKey(undefined)).toBeNull();
  });
});
