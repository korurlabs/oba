import { describe, expect, test } from "bun:test";
import {
  EmailTypeSchema,
  createMailSenderFormSchema,
  emptyMailSenderFormValues,
  mailSenderListItemSchema,
  mailSenderRequestSchema,
  toMailSenderRequest,
  type MailSenderFormValues,
} from "./index";

const schema = createMailSenderFormSchema((key: string) => key);

function errorsOf(values: MailSenderFormValues): Record<string, string> {
  const result = schema.safeParse(values);
  if (result.success) return {};
  return Object.fromEntries(
    result.error.issues.map((issue) => [String(issue.path[0]), issue.message])
  );
}

// Field for field the MailSenderListResponse of the API.
const listItem = {
  id: "8b3e1f2a-4c5d-4e6f-9a0b-1c2d3e4f5a6b",
  mailAddress: "jane.doe@example.com",
  type: "PERSONAL",
  addedBy: "2f6c1a9e-3b4d-4e5f-8a7b-9c0d1e2f3a4b",
  createdAt: "2026-11-12T09:00:00Z",
} as const;

describe("createMailSenderFormSchema", () => {
  test("a mail address passes", () => {
    expect(errorsOf({ type: "PERSONAL", mailAddress: "jane.doe@example.com" })).toEqual({});
  });

  test("the address is trimmed", () => {
    expect(schema.parse({ type: "PERSONAL", mailAddress: "  jane.doe@example.com " }).mailAddress).toBe(
      "jane.doe@example.com"
    );
  });

  test("a blank address is required", () => {
    expect(errorsOf({ type: "PERSONAL", mailAddress: "   " })).toEqual({
      mailAddress: "errors.mailAddressRequired",
    });
  });

  test("a malformed address is refused", () => {
    expect(errorsOf({ type: "PERSONAL", mailAddress: "jane.doe" })).toEqual({
      mailAddress: "errors.mailAddressInvalid",
    });
  });

  test("an address over 254 characters is refused", () => {
    const tooLong = `${"a".repeat(60)}@${"b".repeat(190)}.com`;
    expect(tooLong.length).toBe(255);
    expect(errorsOf({ type: "PERSONAL", mailAddress: tooLong }).mailAddress).toBe("errors.mailAddressMax");
  });
});

describe("EmailTypeSchema", () => {
  test.each(["PERSONAL", "SHARED"])("accepts %s", (value) => {
    expect(EmailTypeSchema.safeParse(value).success).toBe(true);
  });

  test.each(["", "personal", "TEAM", null, undefined, 1])("refuses %p", (value) => {
    expect(EmailTypeSchema.safeParse(value).success).toBe(false);
  });
});

describe("type in the form schema", () => {
  test("a shared type passes", () => {
    expect(errorsOf({ type: "SHARED", mailAddress: "sales@example.com" })).toEqual({});
  });

  test("a missing type is refused", () => {
    const result = schema.safeParse({ mailAddress: "sales@example.com" });
    expect(result.success).toBe(false);
    expect(result.error?.issues.some((issue) => issue.path[0] === "type")).toBe(true);
  });

  test("an unknown type is refused", () => {
    expect(schema.safeParse({ type: "TEAM", mailAddress: "sales@example.com" }).success).toBe(false);
  });
});

describe("form mapping", () => {
  test("a new sender starts empty, as a personal mailbox", () => {
    expect(emptyMailSenderFormValues()).toEqual({ type: "PERSONAL", mailAddress: "" });
  });

  test("a shared request carries its type", () => {
    expect(toMailSenderRequest({ type: "SHARED", mailAddress: "sales@example.com" })).toEqual({
      type: "SHARED",
      mailAddress: "sales@example.com",
    });
  });

  test("the request schema refuses a missing or unknown type", () => {
    expect(mailSenderRequestSchema.safeParse({ mailAddress: "sales@example.com" }).success).toBe(false);
    expect(
      mailSenderRequestSchema.safeParse({ type: "TEAM", mailAddress: "sales@example.com" }).success
    ).toBe(false);
  });

  test("the request carries the type and the address and nothing else", () => {
    const request = toMailSenderRequest({ type: "PERSONAL", mailAddress: "jane.doe@example.com" });
    expect(request).toEqual({ type: "PERSONAL", mailAddress: "jane.doe@example.com" });
    expect(mailSenderRequestSchema.safeParse(request).success).toBe(true);
  });
});

describe("mailSenderListItemSchema", () => {
  test("parses the API's list item", () => {
    expect(mailSenderListItemSchema.parse(listItem)).toEqual(listItem);
  });

  test("parses a shared item", () => {
    expect(mailSenderListItemSchema.parse({ ...listItem, type: "SHARED" }).type).toBe("SHARED");
  });

  test("refuses an item without a type or with an unknown one", () => {
    const { type: _type, ...withoutType } = listItem;
    expect(mailSenderListItemSchema.safeParse(withoutType).success).toBe(false);
    expect(mailSenderListItemSchema.safeParse({ ...listItem, type: "TEAM" }).success).toBe(false);
  });

  test("refuses a creation time without an offset", () => {
    expect(
      mailSenderListItemSchema.safeParse({ ...listItem, createdAt: "2026-11-12T09:00:00" }).success
    ).toBe(false);
  });
});
