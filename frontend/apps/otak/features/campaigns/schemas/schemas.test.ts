import { describe, expect, test } from "bun:test";
import {
  campaignContentRequestSchema,
  campaignContentSchema,
  campaignContentToFormValues,
  campaignDetailSchema,
  campaignRequestSchema,
  campaignStatusDatesIssue,
  campaignToFormValues,
  canMarkContentPublished,
  createCampaignContentFormSchema,
  createCampaignFormSchema,
  emptyCampaignContentFormValues,
  emptyCampaignFormValues,
  instantToLocalInput,
  isCampaignDateRangeValid,
  localInputToInstant,
  toCampaignContentRequest,
  toCampaignRequest,
  toMarkPublishedRequest,
  type CampaignContent,
  type CampaignContentFormValues,
  type CampaignDetail,
  type CampaignFormValues,
} from ".";

const NOW = new Date("2026-11-25T12:00:00Z");
const campaignForm = createCampaignFormSchema((key: string) => key);
const contentForm = createCampaignContentFormSchema(
  (key: string) => key,
  () => NOW
);

function errorsOf(
  schema: typeof campaignForm | typeof contentForm,
  values: CampaignFormValues | CampaignContentFormValues
): Record<string, string> {
  const result = schema.safeParse(values);
  if (result.success) return {};
  const errors: Record<string, string> = {};
  for (const issue of result.error.issues) {
    errors[String(issue.path[0])] = issue.message;
  }
  return errors;
}

// Field for field the CampaignDetailResponse of the API.
const campaign: CampaignDetail = {
  id: "c-1",
  name: "De wacht",
  description: "Post about the night watch",
  startDate: "2026-12-01",
  endDate: "2026-12-31",
  owner: { id: "u-1", name: "Jan de Vries" },
  eventId: "e-1",
  icpId: null,
  contentCount: 2,
  createdAt: "2026-11-20T09:00:00Z",
  updatedAt: "2026-11-21T09:00:00Z",
};

// Field for field the CampaignContentResponse of the API.
const content: CampaignContent = {
  id: "cc-1",
  campaignId: "c-1",
  legalEntityId: "le-1",
  legalEntityShortName: "Korur B.V.",
  channel: "LINKEDIN",
  language: "NL",
  text: "Hallo",
  status: "SCHEDULED",
  plannedAt: "2026-12-01T09:00:00Z",
  publishedAt: null,
  postUrl: null,
  createdAt: "2026-11-20T09:00:00Z",
  updatedAt: "2026-11-21T09:00:00Z",
};

describe("response schemas", () => {
  test("parse the API's campaign and content", () => {
    expect(campaignDetailSchema.parse(campaign)).toEqual(campaign);
    expect(campaignContentSchema.parse(content)).toEqual(content);
  });
});

describe("isCampaignDateRangeValid", () => {
  test("an end before the start is refused; a missing day never is", () => {
    expect(isCampaignDateRangeValid("2026-12-02", "2026-12-01")).toBe(false);
    expect(isCampaignDateRangeValid("2026-12-01", "2026-12-01")).toBe(true);
    expect(isCampaignDateRangeValid(null, "2026-12-01")).toBe(true);
    expect(isCampaignDateRangeValid("2026-12-01", null)).toBe(true);
  });
});

describe("campaign form", () => {
  const valid: CampaignFormValues = { ...emptyCampaignFormValues(), name: "De wacht" };

  test("needs a name and nothing else", () => {
    expect(errorsOf(campaignForm, valid)).toEqual({});
    expect(errorsOf(campaignForm, { ...valid, name: "  " })).toEqual({
      name: "errors.nameRequired",
    });
  });

  test("takes a long name and description, as the server has no cap", () => {
    const long = "x".repeat(5000);
    expect(errorsOf(campaignForm, { ...valid, name: long, description: long })).toEqual({});
  });

  test("refuses an end day before the start day on the end day", () => {
    expect(
      errorsOf(campaignForm, { ...valid, startDate: "2026-12-02", endDate: "2026-12-01" })
    ).toEqual({ endDate: "errors.dateRange" });
  });

  test("maps to the request with nulls for empty fields and back", () => {
    expect(toCampaignRequest(valid)).toEqual({
      name: "De wacht",
      description: null,
      startDate: null,
      endDate: null,
      ownerUserId: null,
      eventId: null,
      icpId: null,
    });
    const values = campaignToFormValues(campaign);
    const request = toCampaignRequest(values);
    expect(request).toEqual({
      name: "De wacht",
      description: "Post about the night watch",
      startDate: "2026-12-01",
      endDate: "2026-12-31",
      ownerUserId: "u-1",
      eventId: "e-1",
      icpId: null,
    });
    expect(campaignRequestSchema.safeParse(request).success).toBe(true);
  });
});

describe("campaignStatusDatesIssue", () => {
  test("a scheduled content needs a planned moment", () => {
    expect(campaignStatusDatesIssue("SCHEDULED", null, null, NOW)).toBe("plannedAtRequired");
  });

  test("a draft or scheduled content takes no published moment", () => {
    expect(campaignStatusDatesIssue("DRAFT", null, "2026-11-24T09:00:00Z", NOW)).toBe(
      "publishedAtNotAllowed"
    );
  });

  test("a published moment is not in the future; a past one is kept", () => {
    expect(campaignStatusDatesIssue("PUBLISHED", null, "2026-11-26T09:00:00Z", NOW)).toBe(
      "publishedAtInFuture"
    );
    expect(campaignStatusDatesIssue("PUBLISHED", null, "2026-11-24T09:00:00Z", NOW)).toBeNull();
    expect(campaignStatusDatesIssue("PUBLISHED", null, null, NOW)).toBeNull();
  });
});

describe("content form", () => {
  const valid: CampaignContentFormValues = {
    ...emptyCampaignContentFormValues("le-1"),
    channel: "LINKEDIN",
    language: "NL",
    text: "Hallo",
  };

  test("needs a legal entity, channel, language and a text that is not blank", () => {
    expect(errorsOf(contentForm, valid)).toEqual({});
    expect(errorsOf(contentForm, emptyCampaignContentFormValues(""))).toEqual({
      legalEntityId: "errors.legalEntityRequired",
      channel: "errors.channelRequired",
      language: "errors.languageRequired",
      text: "errors.textRequired",
    });
  });

  test("takes a text and post address of any length", () => {
    const longText = "x".repeat(10000);
    const longUrl = `https://example.com/${"p".repeat(2000)}`;
    expect(errorsOf(contentForm, { ...valid, text: longText, postUrl: longUrl })).toEqual({});
  });

  test("refuses a post address that is not a web address", () => {
    expect(errorsOf(contentForm, { ...valid, postUrl: "linkedin" })).toEqual({
      postUrl: "errors.postUrlInvalid",
    });
  });

  test("shows a status rule on the moment it is about", () => {
    expect(errorsOf(contentForm, { ...valid, status: "SCHEDULED" })).toEqual({
      plannedAt: "errors.plannedAtRequired",
    });
    expect(
      errorsOf(contentForm, {
        ...valid,
        status: "PUBLISHED",
        publishedAt: instantToLocalInput("2026-11-26T09:00:00Z"),
      })
    ).toEqual({ publishedAt: "errors.publishedAtInFuture" });
  });

  test("maps a stored content to the form and back unchanged", () => {
    const values = campaignContentToFormValues(content);
    const request = toCampaignContentRequest(values, "LINKEDIN", "NL");
    expect(request).toEqual({
      legalEntityId: "le-1",
      channel: "LINKEDIN",
      language: "NL",
      text: "Hallo",
      status: "SCHEDULED",
      plannedAt: "2026-12-01T09:00:00.000Z",
      publishedAt: null,
      postUrl: null,
    });
    expect(campaignContentRequestSchema.safeParse(request).success).toBe(true);
  });
});

describe("mark as published", () => {
  test("only a draft or scheduled content can be marked", () => {
    expect(canMarkContentPublished({ status: "DRAFT" })).toBe(true);
    expect(canMarkContentPublished({ status: "SCHEDULED" })).toBe(true);
    expect(canMarkContentPublished({ status: "PUBLISHED" })).toBe(false);
    expect(canMarkContentPublished({ status: "ARCHIVED" })).toBe(false);
  });

  test("sends PUBLISHED with no published moment, so the server sets now", () => {
    expect(toMarkPublishedRequest(content)).toEqual({
      legalEntityId: "le-1",
      channel: "LINKEDIN",
      language: "NL",
      text: "Hallo",
      status: "PUBLISHED",
      plannedAt: "2026-12-01T09:00:00Z",
      publishedAt: null,
      postUrl: null,
    });
  });
});

describe("datetime-local inputs", () => {
  test("round-trip an instant through the viewer's zone", () => {
    const instant = "2026-12-01T09:00:00.000Z";
    expect(localInputToInstant(instantToLocalInput(instant))).toBe(instant);
  });

  test("an empty or broken input gives null, and null gives an empty input", () => {
    expect(localInputToInstant("")).toBeNull();
    expect(localInputToInstant("2026-12-01")).toBeNull();
    expect(instantToLocalInput(null)).toBe("");
  });
});
