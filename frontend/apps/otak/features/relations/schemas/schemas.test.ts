import { describe, expect, test } from "bun:test";
import {
  createRelationFormSchema,
  emptyRelationFormValues,
  relationAttachmentSchema,
  relationDetailSchema,
  relationListItemSchema,
  RelationSourceSchema,
  relationToFormValues,
  relationUpdateRequestSchema,
  toRelationCreateRequest,
  toRelationUpdateRequest,
  type RelationFormValues,
} from "./index";

const schema = createRelationFormSchema((key: string) => key);

function errorsOf(values: RelationFormValues): Record<string, string> {
  const result = schema.safeParse(values);
  if (result.success) return {};
  return Object.fromEntries(
    result.error.issues.map((issue) => [String(issue.path[0]), issue.message])
  );
}

const valid: RelationFormValues = { ...emptyRelationFormValues("LEAD"), name: "Acme" };

describe("relation form schema", () => {
  test("name and type are required", () => {
    expect(errorsOf(emptyRelationFormValues())).toEqual({
      name: "errors.nameRequired",
      type: "errors.typeRequired",
    });
  });

  test("the website must be an http(s) URL with a dotted host", () => {
    expect(errorsOf({ ...valid, website: "acme.com" }).website).toBe("errors.websiteInvalid");
    expect(errorsOf({ ...valid, website: "https://acme" }).website).toBe("errors.websiteInvalid");
    expect(errorsOf({ ...valid, website: "https://acme.com/about" })).toEqual({});
  });

  test("the country is an ISO 3166-1 alpha-2 code or empty", () => {
    expect(schema.parse({ ...valid, country: "NL" }).country).toBe("NL");
    expect(errorsOf({ ...valid, country: "" })).toEqual({});
    const unknownCountry = "XX" as unknown as RelationFormValues["country"];
    expect(errorsOf({ ...valid, country: unknownCountry }).country).toBeDefined();
  });

  test("a country outside the server's list is refused", () => {
    const lowerCase = "nl" as unknown as RelationFormValues["country"];
    expect(errorsOf({ ...valid, country: lowerCase }).country).toBeDefined();
  });

  test("the ICP is an id or empty", () => {
    expect(errorsOf({ ...valid, icpId: "" })).toEqual({});
    expect(errorsOf({ ...valid, icpId: "7d0c6a4e-1b6a-4a39-8a55-0d7f4a1c9e11" })).toEqual({});
  });

  test("the expected value is euros: not negative, two decimals, below 10^10", () => {
    expect(errorsOf({ ...valid, expectedValue: -1 }).expectedValue).toBe(
      "errors.expectedValueNegative"
    );
    expect(errorsOf({ ...valid, expectedValue: 1.234 }).expectedValue).toBe(
      "errors.expectedValueDecimals"
    );
    expect(errorsOf({ ...valid, expectedValue: 10_000_000_000 }).expectedValue).toBe(
      "errors.expectedValueTooLarge"
    );
    expect(errorsOf({ ...valid, expectedValue: 9_999_999_999.99 })).toEqual({});
  });
});

describe("toRelationCreateRequest", () => {
  test("empty inputs are sent as null", () => {
    const request = toRelationCreateRequest(schema.parse({ ...valid, notes: "   " }), "LEAD");
    expect(request).toEqual({
      type: "LEAD",
      name: "Acme",
      website: null,
      sector: null,
      size: null,
      registrationNumber: null,
      vatNumber: null,
      street: null,
      postalCode: null,
      city: null,
      country: null,
      source: null,
      expectedValue: null,
      ownerUserId: null,
      icpId: null,
      eventId: null,
      campaignId: null,
      notes: null,
    });
  });
});

describe("relation request and response schemas", () => {
  test("the update request carries icpId and a country code, both nullable", () => {
    const base = toRelationUpdateRequest(schema.parse(valid));
    expect(relationUpdateRequestSchema.safeParse(base).success).toBe(true);
    const linked = toRelationUpdateRequest(
      schema.parse({ ...valid, icpId: "icp-1", country: "NL" })
    );
    expect(linked.icpId).toBe("icp-1");
    expect(linked.country).toBe("NL");
    expect(relationUpdateRequestSchema.safeParse(linked).success).toBe(true);
    expect(relationUpdateRequestSchema.safeParse({ ...base, country: "XX" }).success).toBe(false);
  });

  test("the update request keeps the event, and an empty event is sent as null", () => {
    const linked = toRelationUpdateRequest(schema.parse({ ...valid, eventId: "event-1" }));
    expect(linked.eventId).toBe("event-1");
    expect(relationUpdateRequestSchema.safeParse(linked).success).toBe(true);
    expect(toRelationUpdateRequest(schema.parse(valid)).eventId).toBeNull();
  });

  test("an edit sends the stored campaign back, and an empty campaign is sent as null", () => {
    const linked = toRelationUpdateRequest(schema.parse({ ...valid, campaignId: "campaign-1" }));
    expect(linked.campaignId).toBe("campaign-1");
    expect(relationUpdateRequestSchema.safeParse(linked).success).toBe(true);
    expect(toRelationUpdateRequest(schema.parse(valid)).campaignId).toBeNull();
  });

  test("a relation can come from a campaign", () => {
    expect(RelationSourceSchema.safeParse("CAMPAIGN").success).toBe(true);
  });

  test("an empty icpId and country are sent as null", () => {
    const request = toRelationUpdateRequest(schema.parse(valid));
    expect(request.icpId).toBeNull();
    expect(request.country).toBeNull();
  });

  test("a stored relation maps its icpId and country to the form", () => {
    const detail = relationDetailSchema.parse({
      id: "r-1",
      name: "Acme",
      type: "LEAD",
      stage: "NEW",
      website: "https://acme.com",
      sector: null,
      size: null,
      registrationNumber: null,
      vatNumber: null,
      street: null,
      postalCode: null,
      city: null,
      country: "NL",
      source: null,
      expectedValue: null,
      owner: null,
      icpId: "icp-1",
      eventId: "event-1",
      campaignId: "campaign-1",
      notes: null,
      createdAt: "2026-11-09T09:00:00Z",
      updatedAt: "2026-11-09T09:30:00Z",
    });
    const values = relationToFormValues(detail);
    expect(values.icpId).toBe("icp-1");
    expect(values.eventId).toBe("event-1");
    expect(values.campaignId).toBe("campaign-1");
    expect(toRelationUpdateRequest(values).campaignId).toBe("campaign-1");
    expect(values.country).toBe("NL");
    expect(values.website).toBe("https://acme.com");
    expect(relationDetailSchema.safeParse({ ...detail, country: "XX" }).success).toBe(false);
  });

  test("a list item carries a nullable website after the name", () => {
    const item = {
      id: "r-1",
      name: "Acme",
      website: null,
      type: "LEAD",
      stage: "NEW",
      owner: null,
      mainPersonContactName: null,
      mainPersonContactEmail: null,
      mainBusinessContactEmail: null,
      expectedValue: null,
      campaignId: null,
      updatedAt: "2026-11-09T09:30:00Z",
    };
    expect(relationListItemSchema.parse(item).website).toBeNull();
    expect(relationListItemSchema.parse({ ...item, website: "https://acme.com" }).website).toBe(
      "https://acme.com"
    );
    const { website: _website, ...withoutWebsite } = item;
    expect(relationListItemSchema.safeParse(withoutWebsite).success).toBe(false);
    const withMails = relationListItemSchema.parse({
      ...item,
      mainPersonContactName: "Jan",
      mainPersonContactEmail: "jan@acme.com",
      mainBusinessContactEmail: "info@acme.com",
    });
    expect(withMails.mainPersonContactEmail).toBe("jan@acme.com");
    expect(withMails.mainBusinessContactEmail).toBe("info@acme.com");
    const { mainBusinessContactEmail: _companyMail, ...withoutCompanyMail } = item;
    expect(relationListItemSchema.safeParse(withoutCompanyMail).success).toBe(false);
  });
});

describe("relationAttachmentSchema", () => {
  const uploaded = {
    id: "a0000000-0000-0000-0000-000000000001",
    url: "https://blob.example/nda.pdf",
    fileName: "nda.pdf",
    contentType: "application/pdf",
    fileSize: 1024,
    entityType: "RELATION",
    entityId: "b0000000-0000-0000-0000-000000000001",
    createdAt: "2026-11-21T08:00:00Z",
    width: null,
    height: null,
    documentId: null,
    source: "RELATION",
    mailId: null,
    outreachTitle: null,
  };

  test("a file of the relation has no mail", () => {
    const parsed = relationAttachmentSchema.parse(uploaded);
    expect(parsed.source).toBe("RELATION");
    expect(parsed.documentId).toBeNull();
  });

  test("a snapshot on a mail carries its document, mail and outreach title", () => {
    const parsed = relationAttachmentSchema.parse({
      ...uploaded,
      entityType: "MAIL",
      entityId: "c0000000-0000-0000-0000-000000000001",
      documentId: "d0000000-0000-0000-0000-000000000001",
      source: "MAIL",
      mailId: "c0000000-0000-0000-0000-000000000001",
      outreachTitle: "Q4 security offers",
    });
    expect(parsed.documentId).toBe("d0000000-0000-0000-0000-000000000001");
    expect(parsed.outreachTitle).toBe("Q4 security offers");
  });

  test("an unknown source is refused", () => {
    expect(relationAttachmentSchema.safeParse({ ...uploaded, source: "TICKET" }).success).toBe(
      false
    );
  });
});
