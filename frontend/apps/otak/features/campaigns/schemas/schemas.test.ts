import { describe, expect, test } from "bun:test";
import {
  campaignDetailSchema,
  campaignRequestSchema,
  campaignStatusDatesIssue,
  campaignToFormValues,
  canMarkCampaignPublished,
  createCampaignFormSchema,
  emptyCampaignFormValues,
  instantToLocalInput,
  localInputToInstant,
  toCampaignRequest,
  toMarkPublishedRequest,
  type CampaignDetail,
  type CampaignFormValues,
} from ".";

const NOW = new Date("2026-11-25T12:00:00Z");
const schema = createCampaignFormSchema(
  (key: string) => key,
  () => NOW
);

function errorsOf(values: CampaignFormValues): Record<string, string> {
  const result = schema.safeParse(values);
  if (result.success) return {};
  return Object.fromEntries(
    result.error.issues.map((issue) => [String(issue.path[0]), issue.message])
  );
}

const valid: CampaignFormValues = {
  ...emptyCampaignFormValues(),
  name: "Security check launch",
  channel: "LINKEDIN",
};

// Field for field the CampaignDetailResponse of the API.
const detail: CampaignDetail = {
  id: "c-1",
  name: "Security check launch",
  channel: "LINKEDIN",
  status: "SCHEDULED",
  plannedAt: "2026-12-01T09:00:00Z",
  publishedAt: null,
  languages: ["NL", "EN"],
  owner: { id: "u-1", name: "Jan de Vries" },
  eventId: "e-1",
  icpId: null,
  createdAt: "2026-11-20T09:00:00Z",
  updatedAt: "2026-11-21T09:00:00Z",
  postUrl: null,
  contents: [
    { language: "NL", text: "Hallo", updatedAt: "2026-11-21T09:00:00Z" },
    { language: "EN", text: "Hello", updatedAt: "2026-11-21T09:00:00Z" },
  ],
};

describe("campaignStatusDatesIssue", () => {
  test("a scheduled campaign needs a planned moment", () => {
    expect(campaignStatusDatesIssue("SCHEDULED", null, null, NOW)).toBe("plannedAtRequired");
    expect(campaignStatusDatesIssue("SCHEDULED", "2026-12-01T09:00:00Z", null, NOW)).toBeNull();
  });

  test("a draft or scheduled campaign takes no published moment", () => {
    const past = "2026-11-01T09:00:00Z";
    expect(campaignStatusDatesIssue("DRAFT", null, past, NOW)).toBe("publishedAtNotAllowed");
    expect(campaignStatusDatesIssue("SCHEDULED", past, past, NOW)).toBe("publishedAtNotAllowed");
  });

  test("a published moment is not in the future", () => {
    expect(campaignStatusDatesIssue("PUBLISHED", null, "2026-11-25T12:00:01Z", NOW)).toBe(
      "publishedAtInFuture"
    );
    expect(campaignStatusDatesIssue("PUBLISHED", null, "2026-11-25T12:00:00Z", NOW)).toBeNull();
    expect(campaignStatusDatesIssue("ARCHIVED", null, "2026-11-01T09:00:00Z", NOW)).toBeNull();
  });

  test("a published campaign without a moment is left to the server's now", () => {
    expect(campaignStatusDatesIssue("PUBLISHED", null, null, NOW)).toBeNull();
  });
});

describe("date and time inputs", () => {
  test("an instant round-trips through the local input to the minute", () => {
    const local = instantToLocalInput("2026-12-01T09:00:00Z");
    expect(local).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/);
    expect(localInputToInstant(local)).toBe("2026-12-01T09:00:00.000Z");
  });

  test("an empty or malformed input is no moment", () => {
    expect(instantToLocalInput(null)).toBe("");
    expect(localInputToInstant("")).toBeNull();
    expect(localInputToInstant("2026-12-01")).toBeNull();
    expect(localInputToInstant("not a date")).toBeNull();
  });
});

describe("campaign form schema", () => {
  test("a named draft on a channel is valid", () => {
    expect(errorsOf(valid)).toEqual({});
  });

  test("the name and the channel are required, the name at most 200 characters", () => {
    expect(errorsOf({ ...valid, name: "  " }).name).toBe("errors.nameRequired");
    expect(errorsOf({ ...valid, name: "n".repeat(201) }).name).toBe("errors.nameMax");
    expect(errorsOf({ ...valid, channel: "" }).channel).toBe("errors.channelRequired");
  });

  test("the post address is http or https and at most 500 characters", () => {
    expect(errorsOf({ ...valid, postUrl: "ftp://x.example" }).postUrl).toBe(
      "errors.postUrlInvalid"
    );
    const long = `https://x.example/${"p".repeat(490)}`;
    expect(errorsOf({ ...valid, postUrl: long }).postUrl).toBe("errors.postUrlMax");
    expect(errorsOf({ ...valid, postUrl: "https://www.linkedin.com/posts/1" })).toEqual({});
  });

  test("a text is at most 20,000 characters", () => {
    const texts = { ...valid.texts, EN: "t".repeat(20_001) };
    expect(errorsOf({ ...valid, texts })).toEqual({ texts: "errors.textMax" });
  });

  test("the status rules are shown on the moment they concern", () => {
    expect(errorsOf({ ...valid, status: "SCHEDULED" }).plannedAt).toBe("errors.plannedAtRequired");
    expect(errorsOf({ ...valid, publishedAt: "2026-11-01T09:00" }).publishedAt).toBe(
      "errors.publishedAtNotAllowed"
    );
    const future = instantToLocalInput("2026-11-26T12:00:00Z");
    expect(errorsOf({ ...valid, status: "PUBLISHED", publishedAt: future }).publishedAt).toBe(
      "errors.publishedAtInFuture"
    );
  });

  test("a malformed moment is refused", () => {
    expect(errorsOf({ ...valid, plannedAt: "tomorrow" }).plannedAt).toBe("errors.dateTimeInvalid");
  });
});

describe("toCampaignRequest", () => {
  test("empty fields are sent as null and blank texts are left out", () => {
    const values: CampaignFormValues = {
      ...valid,
      status: "PUBLISHED",
      texts: { NL: "Hallo", TR: "   ", EN: " Hello " },
    };
    expect(toCampaignRequest(values, "LINKEDIN")).toEqual({
      name: "Security check launch",
      channel: "LINKEDIN",
      status: "PUBLISHED",
      plannedAt: null,
      publishedAt: null,
      postUrl: null,
      ownerUserId: null,
      eventId: null,
      icpId: null,
      contents: [
        { language: "NL", text: "Hallo" },
        { language: "EN", text: " Hello " },
      ],
    });
  });

  test("a published status without a moment sends null, never the browser's now", () => {
    const request = toCampaignRequest({ ...valid, status: "PUBLISHED" }, "LINKEDIN");
    expect(request.publishedAt).toBeNull();
  });

  test("a stored campaign maps to the form and back unchanged", () => {
    const parsed = campaignDetailSchema.parse(detail);
    const request = toCampaignRequest(campaignToFormValues(parsed), "LINKEDIN");
    expect(request).toMatchObject({
      status: "SCHEDULED",
      plannedAt: "2026-12-01T09:00:00.000Z",
      ownerUserId: "u-1",
      eventId: "e-1",
      contents: [
        { language: "NL", text: "Hallo" },
        { language: "EN", text: "Hello" },
      ],
    });
  });
});

describe("mark as published", () => {
  test("only a draft or scheduled campaign can be marked", () => {
    expect(canMarkCampaignPublished({ status: "DRAFT" })).toBe(true);
    expect(canMarkCampaignPublished({ status: "SCHEDULED" })).toBe(true);
    expect(canMarkCampaignPublished({ status: "PUBLISHED" })).toBe(false);
    expect(canMarkCampaignPublished({ status: "ARCHIVED" })).toBe(false);
  });

  test("the request keeps every field and sends no published moment", () => {
    const request = toMarkPublishedRequest(detail);
    expect(request).toEqual({
      name: detail.name,
      channel: "LINKEDIN",
      status: "PUBLISHED",
      plannedAt: "2026-12-01T09:00:00Z",
      publishedAt: null,
      postUrl: null,
      ownerUserId: "u-1",
      eventId: "e-1",
      icpId: null,
      contents: [
        { language: "NL", text: "Hallo" },
        { language: "EN", text: "Hello" },
      ],
    });
    expect(campaignRequestSchema.safeParse(request).success).toBe(true);
  });
});

describe("campaign request schema", () => {
  test("a text per language at most once", () => {
    const request = toMarkPublishedRequest(detail);
    const repeated = {
      ...request,
      contents: [
        { language: "NL", text: "a" },
        { language: "NL", text: "b" },
      ],
    };
    expect(campaignRequestSchema.safeParse(repeated).success).toBe(false);
  });
});
