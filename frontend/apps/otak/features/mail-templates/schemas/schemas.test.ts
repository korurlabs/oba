import { describe, expect, test } from "bun:test";
import {
  createMailTemplateFormSchema,
  emptyMailTemplateFormValues,
  mailTemplateDetailSchema,
  mailTemplateToFormValues,
  toMailTemplateRequest,
  type MailTemplateDetail,
  type MailTemplateFormValues,
} from ".";

const t = (key: string): string => `t:${key}`;
const schema = createMailTemplateFormSchema(t);

const valid: MailTemplateFormValues = {
  name: "First contact",
  language: "EN",
  subject: "Hello from Korur",
  body: "Dear reader,\n\nWe would like to meet.",
};

// Field for field the MailTemplateDetailResponse of the API.
const detail: MailTemplateDetail = {
  id: "mt-1",
  ...valid,
  updatedAt: "2026-11-17T09:30:00Z",
  createdAt: "2026-11-17T09:00:00Z",
};

function messages(values: MailTemplateFormValues): Record<string, string> {
  const result = schema.safeParse(values);
  if (result.success) return {};
  return Object.fromEntries(
    result.error.issues.map((issue) => [issue.path.join("."), issue.message])
  );
}

describe("mail template form schema", () => {
  test("a complete mail template is valid", () => {
    expect(messages(valid)).toEqual({});
  });

  test("name, subject and body are required", () => {
    expect(messages({ ...emptyMailTemplateFormValues(), subject: " " })).toEqual({
      name: "t:errors.nameRequired",
      subject: "t:errors.subjectRequired",
      body: "t:errors.bodyRequired",
    });
  });

  test("values over the server's limits are refused", () => {
    expect(
      messages({
        ...valid,
        name: "n".repeat(101),
        subject: "s".repeat(256),
        body: "b".repeat(10001),
      })
    ).toEqual({
      name: "t:errors.nameMax",
      subject: "t:errors.subjectMax",
      body: "t:errors.bodyMax",
    });
  });
});

describe("mapping", () => {
  test("the detail of the API parses and fills the edit form", () => {
    expect(mailTemplateDetailSchema.parse(detail)).toEqual(detail);
    expect(mailTemplateToFormValues(detail)).toEqual(valid);
  });

  test("the request carries the subject and body as typed", () => {
    expect(toMailTemplateRequest({ ...valid, body: "  Text  " })).toEqual({
      ...valid,
      body: "  Text  ",
    });
  });
});
