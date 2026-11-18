import { describe, expect, test } from "bun:test";
import {
  contactListItemSchema,
  contactRequestSchema,
  contactToFormValues,
  createContactFormSchema,
  emptyContactFormValues,
  toContactRequest,
  type ContactFormValues,
  type ContactListItem,
} from ".";

const t = (key: string): string => `t:${key}`;

function values(overrides: Partial<ContactFormValues> = {}): ContactFormValues {
  return { ...emptyContactFormValues(), name: "Jan", ...overrides };
}

function stored(overrides: Partial<ContactListItem> = {}): ContactListItem {
  return {
    id: "contact-1",
    relationId: "relation-1",
    name: "Jan",
    kind: "PERSON",
    role: null,
    email: null,
    emailType: null,
    phone: null,
    linkedinUrl: null,
    decisionMaker: false,
    mainPersonContact: false,
    mainBusinessContact: false,
    sourceUrl: null,
    ...overrides,
  };
}

describe("emptyContactFormValues", () => {
  test("a new contact is a person without a main flag", () => {
    const empty = emptyContactFormValues();
    expect(empty.kind).toBe("PERSON");
    expect(empty.mainPersonContact).toBe(false);
    expect(empty.mainBusinessContact).toBe(false);
  });
});

describe("createContactFormSchema", () => {
  test("refuses an unknown kind", () => {
    const result = createContactFormSchema(t).safeParse({ ...values(), kind: "ROBOT" });
    expect(result.success).toBe(false);
  });

  test("accepts a business contact", () => {
    const result = createContactFormSchema(t).safeParse(values({ kind: "BUSINESS" }));
    expect(result.success).toBe(true);
  });
});

describe("toContactRequest", () => {
  test("sends the kind", () => {
    expect(toContactRequest(values({ kind: "BUSINESS" })).kind).toBe("BUSINESS");
  });

  test("a main person contact keeps its person flag", () => {
    const request = toContactRequest(values({ mainPersonContact: true }));
    expect(request.mainPersonContact).toBe(true);
    expect(request.mainBusinessContact).toBe(false);
  });

  test("a main business contact keeps its business flag", () => {
    const request = toContactRequest(values({ kind: "BUSINESS", mainBusinessContact: true }));
    expect(request.mainPersonContact).toBe(false);
    expect(request.mainBusinessContact).toBe(true);
  });

  test("a main person changed to business drops the person flag", () => {
    const form = contactToFormValues(stored({ mainPersonContact: true }));
    const request = toContactRequest({ ...form, kind: "BUSINESS" });
    expect(request.kind).toBe("BUSINESS");
    expect(request.mainPersonContact).toBe(false);
    expect(request.mainBusinessContact).toBe(false);
  });

  test("a main business changed to person drops the business flag", () => {
    const form = contactToFormValues(stored({ kind: "BUSINESS", mainBusinessContact: true }));
    const request = toContactRequest({ ...form, kind: "PERSON" });
    expect(request.mainPersonContact).toBe(false);
    expect(request.mainBusinessContact).toBe(false);
  });
});

describe("contactListItemSchema", () => {
  test("parses the kind and both main flags", () => {
    const parsed = contactListItemSchema.parse(
      stored({ kind: "BUSINESS", mainBusinessContact: true })
    );
    expect(parsed.kind).toBe("BUSINESS");
    expect(parsed.mainPersonContact).toBe(false);
    expect(parsed.mainBusinessContact).toBe(true);
  });

  test("refuses a row without a kind", () => {
    const withoutKind: Partial<ContactListItem> = stored();
    delete withoutKind.kind;
    expect(contactListItemSchema.safeParse(withoutKind).success).toBe(false);
  });
});

describe("contactRequestSchema", () => {
  test("the request built from the form parses", () => {
    expect(contactRequestSchema.safeParse(toContactRequest(values())).success).toBe(true);
  });

  test.each(["kind", "decisionMaker", "mainPersonContact", "mainBusinessContact"] as const)(
    "refuses a request without %s",
    (field) => {
      const request: Record<string, unknown> = { ...toContactRequest(values()) };
      delete request[field];
      expect(contactRequestSchema.safeParse(request).success).toBe(false);
      expect(contactRequestSchema.safeParse({ ...request, [field]: null }).success).toBe(false);
    }
  );

  test("an edit of the main person sends its kind and the stored flag back", () => {
    const request = toContactRequest(
      contactToFormValues(stored({ mainPersonContact: true, decisionMaker: true }))
    );
    expect(request).toMatchObject({
      kind: "PERSON",
      decisionMaker: true,
      mainPersonContact: true,
      mainBusinessContact: false,
    });
  });
});
