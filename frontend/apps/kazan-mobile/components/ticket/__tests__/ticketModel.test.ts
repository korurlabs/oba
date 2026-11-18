import { describe, expect, it } from "bun:test";

import {
  activeMentionQuery,
  canEditComment,
  commentsReadable,
  isCommentEditGone,
  commentSegments,
  decodeMentions,
  descriptionText,
  encodeMentions,
  hostOf,
  insertMention,
  isCommentEdited,
  mentionCandidates,
  normalizeTicketKey,
  orderComments,
  typedMentionQuery,
  orderSubTickets,
  personName,
  subTicketProgress,
  ticketDeleteMessageKey,
  ticketKeyFromLink,
  ticketWebPath,
  type MentionPerson,
} from "@/components/ticket/ticketModel";
import { ticketHref, ticketPath } from "@/components/ticket/ticketRoutes";
import { TICKET_COMMENTS_ENABLED } from "@/lib/features";
import type { Comment, SubTicket } from "@/lib/schemas/tickets";

const HOST = "kazan.korur.com";
const AYSE: MentionPerson = { userId: "11111111-1111-1111-1111-111111111111", name: "Ayşe" };
const AYSE_KAYA: MentionPerson = {
  userId: "22222222-2222-2222-2222-222222222222",
  name: "Ayşe Kaya",
};

function comment(overrides: Partial<Comment>): Comment {
  return {
    id: "c1",
    createdAt: "2026-11-04T10:00:00Z",
    updatedAt: "2026-11-04T10:00:00Z",
    content: "",
    ticketId: "t1",
    authorId: "me",
    ...overrides,
  };
}

function subTicket(id: string, status: string): SubTicket {
  return { id, key: `KZN-${id}`, title: id, type: "FEATURE", status } as SubTicket;
}

describe("normalizeTicketKey", () => {
  it("upper-cases, trims and decodes a key", () => {
    expect(normalizeTicketKey(" kzn-12 ")).toBe("KZN-12");
    expect(normalizeTicketKey("MY_BOARD%2D7")).toBe("MY_BOARD-7");
  });

  it("takes the first value of a repeated parameter", () => {
    expect(normalizeTicketKey(["ABC-1", "ABC-2"])).toBe("ABC-1");
  });

  it("refuses anything that is not a key", () => {
    expect(normalizeTicketKey("new")).toBeNull();
    expect(normalizeTicketKey("ABC-")).toBeNull();
    expect(normalizeTicketKey("ABC 1")).toBeNull();
    expect(normalizeTicketKey("%E0%A4%A")).toBeNull();
    expect(normalizeTicketKey(undefined)).toBeNull();
  });
});

describe("ticketKeyFromLink", () => {
  it("reads the app's own link", () => {
    expect(ticketKeyFromLink("kazan://ticket/kzn-12", HOST)).toBe("KZN-12");
    expect(ticketKeyFromLink("kazan://ticket/KZN-12/?from=push", HOST)).toBe("KZN-12");
  });

  it("reads a Kazan web ticket page in every web locale", () => {
    for (const locale of ["en", "nl", "tr"]) {
      expect(ticketKeyFromLink(`https://${HOST}/${locale}/ticket/KZN-12`, HOST)).toBe("KZN-12");
    }
    expect(ticketKeyFromLink(`https://KAZAN.korur.com/en/ticket/KZN-12#comments`, HOST)).toBe(
      "KZN-12"
    );
  });

  it("leaves other links alone", () => {
    expect(ticketKeyFromLink(`https://${HOST}/en/ticket/new`, HOST)).toBeNull();
    expect(ticketKeyFromLink(`https://${HOST}/de/ticket/KZN-12`, HOST)).toBeNull();
    expect(ticketKeyFromLink(`https://${HOST}/en/board/KZN-12`, HOST)).toBeNull();
    expect(ticketKeyFromLink("https://evil.example/en/ticket/KZN-12", HOST)).toBeNull();
    expect(ticketKeyFromLink(`https://${HOST}/en/ticket/KZN-12`, null)).toBeNull();
    expect(ticketKeyFromLink("/boards", HOST)).toBeNull();
  });
});

describe("links and paths", () => {
  it("builds the web path in a web locale, English otherwise", () => {
    expect(ticketWebPath("nl", "KZN-12")).toBe("/nl/ticket/KZN-12");
    expect(ticketWebPath("de", "KZN-12")).toBe("/en/ticket/KZN-12");
  });

  it("builds the app path and href", () => {
    expect(ticketPath("KZN-12")).toBe("/ticket/KZN-12");
    expect(ticketHref("KZN-12")).toEqual({
      pathname: "/ticket/[ticketKey]",
      params: { ticketKey: "KZN-12" },
    });
  });

  it("reads the host of a URL", () => {
    expect(hostOf("https://Kazan.korur.com:443/en")).toBe("kazan.korur.com");
    expect(hostOf("kazan://ticket/KZN-1")).toBeNull();
  });
});

describe("names", () => {
  it("prefers the full name, then the email, then the fallback", () => {
    expect(personName({ firstName: "Ayşe", lastName: "Kaya" }, "?")).toBe("Ayşe Kaya");
    expect(personName({ firstName: null, email: "a@example.com" }, "?")).toBe("a@example.com");
    expect(personName(null, "?")).toBe("?");
  });
});

describe("sub-tickets", () => {
  it("counts the done ones", () => {
    expect(subTicketProgress([subTicket("1", "DONE"), subTicket("2", "TODO")])).toEqual({
      done: 1,
      total: 2,
      percent: 50,
    });
    expect(subTicketProgress(null)).toEqual({ done: 0, total: 0, percent: 0 });
  });

  it("lists open ones first and keeps the order within each group", () => {
    const ordered: SubTicket[] = orderSubTickets([
      subTicket("1", "DONE"),
      subTicket("2", "TODO"),
      subTicket("3", "IN_PROGRESS"),
    ]);
    expect(ordered.map((ticket) => ticket.id)).toEqual(["2", "3", "1"]);
  });
});

describe("descriptionText", () => {
  it("replaces images and drops HTML", () => {
    const markdown: string =
      'Steps:<br/>1. Open <b>board</b>\n\n\n\n<img src="x.png" alt="x"> and ![shot](y.png)&nbsp;end';
    expect(descriptionText(markdown, "[Image]")).toBe(
      "Steps:\n1. Open board\n\n[Image] and [Image] end"
    );
  });

  it("is empty without a description", () => {
    expect(descriptionText(null, "[Image]")).toBe("");
  });
});

describe("comments", () => {
  it("lets only the author edit", () => {
    expect(canEditComment(comment({ authorId: "me" }), "me", true)).toBe(true);
    expect(canEditComment(comment({ authorId: "other" }), "me", true)).toBe(false);
    expect(canEditComment(comment({ authorId: "me" }), undefined, true)).toBe(false);
  });

  it("lets the author edit only while they may write on the board", () => {
    expect(canEditComment(comment({ authorId: "me" }), "me", false)).toBe(false);
  });

  it("ends an edit only when the comment can no longer be reached", () => {
    expect(isCommentEditGone({ kind: "not-found" })).toBe(true);
    expect(isCommentEditGone({ kind: "network" })).toBe(false);
    expect(isCommentEditGone({ kind: "server" })).toBe(false);
    expect(isCommentEditGone({ kind: "forbidden" })).toBe(false);
    expect(isCommentEditGone({ kind: "validation" })).toBe(false);
  });

  it("marks a comment edited only after a real change", () => {
    expect(isCommentEdited(comment({ updatedAt: "2026-11-04T10:00:00.500Z" }))).toBe(false);
    expect(isCommentEdited(comment({ updatedAt: "2026-11-04T10:05:00Z" }))).toBe(true);
  });

  it("orders comments oldest first", () => {
    const ordered: Comment[] = orderComments([
      comment({ id: "b", createdAt: "2026-11-04T11:00:00Z" }),
      comment({ id: "a", createdAt: "2026-11-04T09:00:00Z" }),
    ]);
    expect(ordered.map((item) => item.id)).toEqual(["a", "b"]);
  });
});

describe("comments switch", () => {
  const TICKET = { boardId: "board-1", ticketId: "ticket-1" };

  it("is off until comments ship on the web too", () => {
    expect(TICKET_COMMENTS_ENABLED).toBe(false);
  });

  it("reads no comments while comments are off", () => {
    expect(commentsReadable(false, "org-1", TICKET)).toBe(false);
  });

  it("reads comments while on, once the organization and the ticket are known", () => {
    expect(commentsReadable(true, "org-1", TICKET)).toBe(true);
    expect(commentsReadable(true, null, TICKET)).toBe(false);
    expect(commentsReadable(true, "org-1", undefined)).toBe(false);
  });

  it("names the comment count in the delete question only while comments are on", () => {
    expect(ticketDeleteMessageKey(true)).toBe("ticketDeleteMessage");
    expect(ticketDeleteMessageKey(false)).toBe("ticketDeleteMessageNoComments");
  });
});

describe("mentions", () => {
  const names: Map<string, string> = new Map([
    [AYSE.userId, AYSE.name],
    [AYSE_KAYA.userId, AYSE_KAYA.name],
  ]);

  it("splits stored text into text and named mentions", () => {
    const unknown: string = "33333333-3333-3333-3333-333333333333";
    expect(commentSegments(`Hi <@${AYSE.userId}>, see <@${unknown}>.`, names)).toEqual([
      { kind: "text", text: "Hi " },
      { kind: "mention", userId: AYSE.userId, name: "Ayşe" },
      { kind: "text", text: ", see " },
      { kind: "mention", userId: unknown, name: null },
      { kind: "text", text: "." },
    ]);
  });

  it("finds the mention being typed", () => {
    expect(activeMentionQuery("Hi @Ay", 6)).toEqual({ start: 3, query: "Ay" });
    expect(activeMentionQuery("@", 1)).toEqual({ start: 0, query: "" });
    expect(activeMentionQuery("Hi @Ayşe K", 10)).toEqual({ start: 3, query: "Ayşe K" });
    expect(activeMentionQuery("mail@korur", 10)).toBeNull();
    expect(activeMentionQuery("Hi @Ayşe Kaya done", 18)).toBeNull();
  });

  it("opens the mention list only once the starting text is changed", () => {
    expect(typedMentionQuery("Thanks @Ayşe", 12, "Thanks @Ayşe")).toBeNull();
    expect(typedMentionQuery("Thanks @Ayşe K", 14, "Thanks @Ayşe")).toEqual({
      start: 7,
      query: "Ayşe K",
    });
    expect(typedMentionQuery("@A", 2, "")).toEqual({ start: 0, query: "A" });
    expect(typedMentionQuery("", 0, "")).toBeNull();
  });

  it("filters candidates by name", () => {
    expect(mentionCandidates([AYSE, AYSE_KAYA], "kaya")).toEqual([AYSE_KAYA]);
    expect(mentionCandidates([AYSE, AYSE_KAYA], "", 1)).toEqual([AYSE]);
  });

  it("inserts a picked person and moves the cursor after the name", () => {
    expect(insertMention("Hi @Ay and", 6, { start: 3, query: "Ay" }, AYSE)).toEqual({
      text: "Hi @Ayşe  and",
      cursor: 9,
    });
  });

  it("encodes picked names as tokens, longest name first", () => {
    expect(encodeMentions("@Ayşe Kaya and @Ayşe", [AYSE, AYSE_KAYA])).toBe(
      `<@${AYSE_KAYA.userId}> and <@${AYSE.userId}>`
    );
  });

  it("decodes tokens for editing and round-trips", () => {
    const stored: string = `Ask <@${AYSE_KAYA.userId}>`;
    const decoded = decodeMentions(stored, names);
    expect(decoded.text).toBe("Ask @Ayşe Kaya");
    expect(decoded.picked).toEqual([AYSE_KAYA]);
    expect(encodeMentions(decoded.text, decoded.picked)).toBe(stored);
  });
});
