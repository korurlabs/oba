import { describe, expect, test } from "bun:test";
import {
  canCompleteOutreach,
  canSendOutreach,
  canWriteMailAttachments,
  createOutreachFormSchema,
  firstRelationOutreachesPage,
  isOutreachEditable,
  outreachDetailSchema,
  outreachListItemSchema,
  outreachQueryRequestSchema,
  outreachRequestSchema,
  outreachToFormValues,
  outreachUpdateRequestSchema,
  toOutreachUpdateRequest,
  type OutreachDetail,
  type OutreachFormValues,
  type OutreachListItem,
} from "./index";

const schema = createOutreachFormSchema((key: string) => key, "MAIL");
const callSchema = createOutreachFormSchema((key: string) => key, "CALL");

function errorsOf(values: OutreachFormValues): Record<string, string> {
  const result = schema.safeParse(values);
  if (result.success) return {};
  return Object.fromEntries(
    result.error.issues.map((issue) => [String(issue.path[0]), issue.message])
  );
}

const valid: OutreachFormValues = {
  title: "Intro security check",
  contactId: "c-1",
  subject: "Hello from Korur",
  body: "Dear reader,\n\nWe would like to meet.",
  language: "NL",
};

// Field for field the OutreachDetailResponse of the API.
const detail: OutreachDetail = {
  id: "7d0c6a4e-1b6a-4a39-8a55-0d7f4a1c9e11",
  title: "Intro security check",
  type: "MAIL",
  status: "DRAFT",
  relationId: "r-1",
  contact: { id: "c-1", name: "Jan de Vries" },
  sentTo: null,
  sentAt: null,
  mailSenderAddress: null,
  mail: {
    id: "m-1",
    mailTemplateId: "t-1",
    subject: "Hello from Korur",
    body: "Dear reader,\n\nWe would like to meet.",
    language: "NL",
  },
  updatedAt: "2026-11-09T09:30:00Z",
  createdAt: "2026-11-09T09:00:00Z",
};

describe("outreach form schema", () => {
  test("a complete form passes", () => {
    expect(errorsOf(valid)).toEqual({});
  });

  test("blank title, subject and body and a missing contact are refused", () => {
    expect(errorsOf({ ...valid, title: "  ", subject: " ", body: "", contactId: "" })).toEqual({
      title: "errors.titleRequired",
      subject: "errors.subjectRequired",
      body: "errors.bodyRequired",
      contactId: "errors.contactRequired",
    });
  });

  test("values over the server's limits are refused", () => {
    expect(
      errorsOf({
        ...valid,
        title: "t".repeat(201),
        subject: "s".repeat(256),
        body: "b".repeat(10001),
      })
    ).toEqual({
      title: "errors.titleMax",
      subject: "errors.subjectMax",
      body: "errors.bodyMax",
    });
  });

  test("a call checks only the title and the contact", () => {
    const call = { ...valid, subject: "", body: "" };
    expect(callSchema.safeParse(call).success).toBe(true);
    expect(callSchema.safeParse({ ...call, title: " ", contactId: "" }).success).toBe(false);
  });
});

describe("response schemas", () => {
  test("the detail of the API parses", () => {
    expect(outreachDetailSchema.parse(detail)).toEqual(detail);
  });

  test("a sent list item parses with its sender and moment", () => {
    // The list item is the detail without the mail.
    const item = outreachListItemSchema.parse(detail);
    const sent: OutreachListItem = {
      ...item,
      status: "SENT",
      sentTo: "jan@example.com",
      sentAt: "2026-11-10T08:00:00Z",
      mailSenderAddress: "sales@example.com",
    };
    expect(outreachListItemSchema.parse(sent)).toEqual(sent);
  });

  test("an unknown status is refused", () => {
    expect(outreachDetailSchema.safeParse({ ...detail, status: "SENDING" }).success).toBe(false);
  });

  test("a done call parses without a mail", () => {
    const call = {
      ...detail,
      type: "CALL",
      status: "SENT",
      sentTo: "+31 6 1234 5678",
      sentAt: "2026-11-10T08:00:00Z",
      mail: null,
    };
    expect(outreachDetailSchema.parse(call)).toEqual(call as OutreachDetail);
  });

  test("a mail without its id is refused", () => {
    const mail = { mailTemplateId: null, subject: "S", body: "B", language: "NL" };
    expect(outreachDetailSchema.safeParse({ ...detail, mail }).success).toBe(false);
  });
});

describe("request schemas", () => {
  test("a create from a template and one by hand both parse", () => {
    const base = {
      relationId: "r-1",
      contactId: "c-1",
      title: "Intro",
      type: "MAIL",
      documentTemplateIds: [],
    };
    expect(
      outreachRequestSchema.safeParse({ ...base, mailTemplateId: "t-1", mail: null }).success
    ).toBe(true);
    expect(
      outreachRequestSchema.safeParse({
        ...base,
        mailTemplateId: null,
        mail: { subject: "S", body: "B", language: "NL" },
      }).success
    ).toBe(true);
  });

  test("a mail by hand without a language is refused on create and update", () => {
    const create = {
      relationId: "r-1",
      contactId: "c-1",
      title: "Intro",
      type: "MAIL",
      documentTemplateIds: [],
    };
    for (const language of [null, undefined]) {
      const mail = { subject: "S", body: "B", language };
      expect(
        outreachRequestSchema.safeParse({ ...create, mailTemplateId: null, mail }).success
      ).toBe(false);
      expect(
        outreachUpdateRequestSchema.safeParse({ contactId: "c-1", title: "Intro", mail }).success
      ).toBe(false);
    }
  });

  test("the update built from the form always carries the language", () => {
    const request = toOutreachUpdateRequest(valid, "MAIL");
    expect(request.mail?.language).toBe("NL");
    expect(outreachUpdateRequestSchema.safeParse(request).success).toBe(true);
  });

  test("the relation card's first page asks 25 rows of the relation", () => {
    expect(outreachQueryRequestSchema.parse(firstRelationOutreachesPage("r-1"))).toEqual({
      relationId: "r-1",
      page: 1,
      amount: 25,
    });
  });
});

describe("form mapping", () => {
  test("a stored outreach fills the edit form and maps back to the update", () => {
    const values = outreachToFormValues(outreachDetailSchema.parse(detail));
    expect(values).toEqual(valid);
    const request = toOutreachUpdateRequest(values, "MAIL");
    expect(outreachUpdateRequestSchema.parse(request)).toEqual({
      contactId: "c-1",
      title: "Intro security check",
      mail: { subject: valid.subject, body: valid.body, language: "NL" },
    });
  });

  test("a WhatsApp outreach fills empty mail fields and updates without a mail", () => {
    const whatsapp = outreachDetailSchema.parse({
      ...detail,
      type: "WHATSAPP",
      mail: null,
    });
    const values = outreachToFormValues(whatsapp);
    expect(values).toEqual({ ...valid, subject: "", body: "", language: "NL" });
    expect(toOutreachUpdateRequest(values, "WHATSAPP")).toEqual({
      contactId: "c-1",
      title: "Intro security check",
      mail: null,
    });
  });

  test("a deleted contact leaves the contact empty", () => {
    const values = outreachToFormValues(outreachDetailSchema.parse({ ...detail, contact: null }));
    expect(values.contactId).toBe("");
  });
});

describe("helpers", () => {
  test("only a SENT outreach is final", () => {
    expect(isOutreachEditable({ status: "DRAFT" })).toBe(true);
    expect(isOutreachEditable({ status: "FAILED" })).toBe(true);
    expect(isOutreachEditable({ status: "SENT" })).toBe(false);
  });

  test("a mail is sent and a WhatsApp message or a call is marked as done, until SENT", () => {
    expect(canSendOutreach({ type: "MAIL", status: "FAILED" })).toBe(true);
    expect(canSendOutreach({ type: "CALL", status: "DRAFT" })).toBe(false);
    expect(canSendOutreach({ type: "MAIL", status: "SENT" })).toBe(false);
    expect(canCompleteOutreach({ type: "WHATSAPP", status: "DRAFT" })).toBe(true);
    expect(canCompleteOutreach({ type: "CALL", status: "SENT" })).toBe(false);
    expect(canCompleteOutreach({ type: "MAIL", status: "DRAFT" })).toBe(false);
  });

  test("only an admin changes the mail attachments, until SENT", () => {
    expect(canWriteMailAttachments({ status: "DRAFT" }, true)).toBe(true);
    expect(canWriteMailAttachments({ status: "FAILED" }, true)).toBe(true);
    expect(canWriteMailAttachments({ status: "SENT" }, true)).toBe(false);
    expect(canWriteMailAttachments({ status: "DRAFT" }, false)).toBe(false);
  });

  test("the detail no longer carries attachments", () => {
    expect("attachments" in outreachDetailSchema.parse({ ...detail, attachments: [] })).toBe(false);
  });
});
