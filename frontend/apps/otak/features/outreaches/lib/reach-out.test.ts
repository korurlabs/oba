import { describe, expect, test } from "bun:test";
import type { ContactEmailType, ContactListItem } from "@/features/contacts/schemas";
import {
  applyTemplate,
  callableContacts,
  emptyReachOutSelection,
  isStepComplete,
  isTemplateUnchanged,
  mailableContacts,
  nextReachOutStep,
  previousReachOutStep,
  reachableContacts,
  reachOutSteps,
  startFromScratch,
  toReachOutRequest,
  type ReachOutSelection,
} from "./reach-out";

function contact(
  id: string,
  email: string | null,
  emailType: ContactEmailType | null = null,
  phone: string | null = null
): ContactListItem {
  return {
    id,
    relationId: "relation-1",
    name: `Contact ${id}`,
    kind: "PERSON",
    role: null,
    email,
    emailType,
    phone,
    linkedinUrl: null,
    decisionMaker: false,
    mainPersonContact: false,
    mainBusinessContact: false,
    sourceUrl: null,
  };
}

const template = {
  id: "template-1",
  name: "First contact",
  subject: "Hello from Korur",
  body: "Dear reader,\n\nWe would like to meet.",
  language: "EN",
} as const;

const withEmail = contact("a", "a@example.com", "COMPANY");
const complete: ReachOutSelection = applyTemplate(
  { ...emptyReachOutSelection(), contactId: "a" },
  template
);

describe("mailableContacts", () => {
  test("keeps contacts with a business or untyped address, in order", () => {
    const contacts = [
      contact("x", null),
      withEmail,
      contact("y", "  "),
      contact("p", "p@private.example", "PERSONAL"),
      contact("s", "info@example.com", "SHARED"),
      contact("z", "z@example.com"),
    ];
    expect(mailableContacts(contacts).map((c) => c.id)).toEqual(["a", "s", "z"]);
  });
});

describe("callableContacts", () => {
  test("keeps contacts with a phone number, in order", () => {
    const contacts = [
      contact("x", "x@example.com"),
      contact("p", null, null, "+31 6 1234 5678"),
      contact("b", null, null, "   "),
      contact("q", "q@example.com", "PERSONAL", "+90 555"),
    ];
    expect(callableContacts(contacts).map((c) => c.id)).toEqual(["p", "q"]);
  });

  test("the channel picks mailable or callable contacts", () => {
    const contacts = [withEmail, contact("p", null, null, "+31 6")];
    expect(reachableContacts(contacts, "MAIL").map((c) => c.id)).toEqual(["a"]);
    expect(reachableContacts(contacts, "WHATSAPP").map((c) => c.id)).toEqual(["p"]);
    expect(reachableContacts(contacts, "CALL").map((c) => c.id)).toEqual(["p"]);
  });
});

describe("mail source", () => {
  test("a template fills subject, body, language and the empty title", () => {
    const selection = applyTemplate(emptyReachOutSelection(), template);
    expect(selection.subject).toBe(template.subject);
    expect(selection.body).toBe(template.body);
    expect(selection.language).toBe("EN");
    expect(selection.title).toBe("First contact");
  });

  test("a typed title is kept when a template is picked", () => {
    const selection = applyTemplate({ ...emptyReachOutSelection(), title: "Mine" }, template);
    expect(selection.title).toBe("Mine");
  });

  test("writing from scratch clears the mail and defaults to NL", () => {
    const selection = startFromScratch(complete);
    expect(selection.source).toBe("scratch");
    expect(selection.subject).toBe("");
    expect(selection.body).toBe("");
    expect(selection.language).toBe("NL");
  });
});

describe("isStepComplete", () => {
  test("nothing but the channel and attachments is answered at the start", () => {
    const empty = emptyReachOutSelection();
    expect(isStepComplete("channel", empty, [withEmail])).toBe(true);
    expect(isStepComplete("contact", empty, [withEmail])).toBe(false);
    expect(isStepComplete("mail", empty, [withEmail])).toBe(false);
    expect(isStepComplete("attachments", empty, [withEmail])).toBe(true);
    expect(isStepComplete("save", empty, [withEmail])).toBe(false);
  });

  test("a full selection completes every step", () => {
    for (const step of ["channel", "contact", "mail", "attachments", "save"] as const) {
      expect(isStepComplete(step, complete, [withEmail])).toBe(true);
    }
  });

  test("a chosen contact that is no longer mailable does not count", () => {
    expect(isStepComplete("contact", complete, [])).toBe(false);
    expect(isStepComplete("save", complete, [])).toBe(false);
  });

  test("a blank or too long subject or body leaves the mail step open", () => {
    expect(isStepComplete("mail", { ...complete, subject: "  " }, [withEmail])).toBe(false);
    expect(isStepComplete("mail", { ...complete, body: "" }, [withEmail])).toBe(false);
    expect(isStepComplete("mail", { ...complete, subject: "s".repeat(256) }, [withEmail])).toBe(
      false
    );
    expect(isStepComplete("mail", { ...complete, body: "b".repeat(10001) }, [withEmail])).toBe(
      false
    );
  });

  test("save needs a title of at most 200 characters", () => {
    expect(isStepComplete("save", { ...complete, title: "   " }, [withEmail])).toBe(false);
    expect(isStepComplete("save", { ...complete, title: "t".repeat(201) }, [withEmail])).toBe(
      false
    );
  });
});

describe("toReachOutRequest", () => {
  test("an untouched template goes by its id", () => {
    expect(isTemplateUnchanged(complete)).toBe(true);
    expect(toReachOutRequest("relation-1", complete)).toEqual({
      relationId: "relation-1",
      contactId: "a",
      title: "First contact",
      type: "MAIL",
      mailTemplateId: "template-1",
      mail: null,
    });
  });

  test("an edited template goes as the text typed, in the template's language", () => {
    const edited = { ...complete, body: "Shorter." };
    expect(isTemplateUnchanged(edited)).toBe(false);
    expect(toReachOutRequest("relation-1", edited)).toMatchObject({
      mailTemplateId: null,
      mail: { subject: template.subject, body: "Shorter.", language: "EN" },
    });
  });

  test("a mail written by hand goes as typed, with the trimmed title", () => {
    const scratch = {
      ...startFromScratch(complete),
      subject: "Hi",
      body: "Body",
      language: "TR" as const,
      title: "  Intro  ",
    };
    expect(toReachOutRequest("relation-1", scratch)).toEqual({
      relationId: "relation-1",
      contactId: "a",
      title: "Intro",
      type: "MAIL",
      mailTemplateId: null,
      mail: { subject: "Hi", body: "Body", language: "TR" },
    });
  });
});

describe("step order", () => {
  test("a mail runs channel, contact, mail, attachments, save", () => {
    expect(nextReachOutStep("channel", "MAIL")).toBe("contact");
    expect(nextReachOutStep("contact", "MAIL")).toBe("mail");
    expect(nextReachOutStep("mail", "MAIL")).toBe("attachments");
    expect(nextReachOutStep("attachments", "MAIL")).toBe("save");
    expect(nextReachOutStep("save", "MAIL")).toBeNull();
    expect(previousReachOutStep("save", "MAIL")).toBe("attachments");
    expect(previousReachOutStep("mail", "MAIL")).toBe("contact");
    expect(previousReachOutStep("channel", "MAIL")).toBeNull();
  });

  test("a WhatsApp message or a call skips the mail and the attachments", () => {
    expect(reachOutSteps("CALL")).toEqual(["channel", "contact", "save"]);
    expect(nextReachOutStep("contact", "WHATSAPP")).toBe("save");
    expect(previousReachOutStep("save", "CALL")).toBe("contact");
  });
});

describe("WhatsApp and call reach-outs", () => {
  const caller = contact("p", null, null, "+31 6 1234 5678");
  const call: ReachOutSelection = {
    ...complete,
    type: "CALL",
    contactId: "p",
    title: "Intro call",
  };

  test("save needs a callable contact and a title, not a mail", () => {
    const noMail = { ...call, source: null, subject: "", body: "" };
    expect(isStepComplete("save", noMail, [caller])).toBe(true);
    expect(isStepComplete("save", noMail, [])).toBe(false);
  });

  test("the request carries the channel without mail or attachments", () => {
    expect(toReachOutRequest("relation-1", call)).toEqual({
      relationId: "relation-1",
      contactId: "p",
      title: "Intro call",
      type: "CALL",
      mailTemplateId: null,
      mail: null,
    });
  });
});
