import { describe, expect, test } from "bun:test";
import {
  createEventFormSchema,
  emptyEventFormValues,
  eventDetailSchema,
  eventRequestSchema,
  eventToFormValues,
  toEventRequest,
  type EventFormValues,
  type EventRequest,
} from "./index";

const formSchema = createEventFormSchema((key: string) => key);

function formErrors(values: EventFormValues): Record<string, string> {
  const result = formSchema.safeParse(values);
  if (result.success) return {};
  return Object.fromEntries(
    result.error.issues.map((issue) => [String(issue.path[0]), issue.message])
  );
}

const validRequest: EventRequest = {
  name: "IGEF 2026",
  type: "EXPO",
  startDate: "2026-11-10",
  endDate: "2026-11-13",
  city: "Ankara",
  country: "TR",
  website: "https://igef.com.tr/",
  notes: null,
};

const validForm: EventFormValues = {
  ...emptyEventFormValues(),
  name: "IGEF 2026",
  type: "EXPO",
  startDate: "2026-11-10",
};

describe("eventRequestSchema", () => {
  test("a full request passes", () => {
    expect(eventRequestSchema.safeParse(validRequest).success).toBe(true);
  });

  test("an end before the start is refused on endDate", () => {
    const result = eventRequestSchema.safeParse({ ...validRequest, endDate: "2026-11-09" });
    expect(result.success).toBe(false);
    expect(result.error?.issues.map((issue) => issue.path)).toEqual([["endDate"]]);
  });

  test("an end on the start day and no end pass", () => {
    expect(eventRequestSchema.safeParse({ ...validRequest, endDate: "2026-11-10" }).success).toBe(
      true
    );
    expect(eventRequestSchema.safeParse({ ...validRequest, endDate: null }).success).toBe(true);
  });

  test("an instant is not a date-only start", () => {
    expect(
      eventRequestSchema.safeParse({ ...validRequest, startDate: "2026-11-10T00:00:00Z" }).success
    ).toBe(false);
  });

  test("a website without http or https is refused", () => {
    expect(eventRequestSchema.safeParse({ ...validRequest, website: "igef.com.tr" }).success).toBe(
      false
    );
  });

  test("a name over 200 characters and empty notes are refused", () => {
    expect(eventRequestSchema.safeParse({ ...validRequest, name: "a".repeat(201) }).success).toBe(
      false
    );
    expect(eventRequestSchema.safeParse({ ...validRequest, notes: "" }).success).toBe(false);
  });
});

describe("createEventFormSchema", () => {
  test("name, type and start date are enough", () => {
    expect(formErrors(validForm)).toEqual({});
  });

  test("an empty form names its required fields", () => {
    expect(formErrors(emptyEventFormValues())).toEqual({
      name: "errors.nameRequired",
      type: "errors.typeRequired",
      startDate: "errors.startDateRequired",
    });
  });

  test("an end before the start is shown on the end date", () => {
    expect(formErrors({ ...validForm, endDate: "2026-11-09" })).toEqual({
      endDate: "errors.dateRange",
    });
  });

  test("an invalid website is shown on its field", () => {
    expect(formErrors({ ...validForm, website: "igef" })).toEqual({
      website: "errors.websiteInvalid",
    });
  });
});

describe("form mapping", () => {
  test("empty optional fields are sent as null and the name trimmed", () => {
    expect(toEventRequest({ ...validForm, name: "  IGEF 2026 ", notes: "   " }, "EXPO")).toEqual({
      name: "IGEF 2026",
      type: "EXPO",
      startDate: "2026-11-10",
      endDate: null,
      city: null,
      country: null,
      website: null,
      notes: null,
    });
  });

  test("a stored event round-trips through the form", () => {
    const detail = eventDetailSchema.parse({
      id: "7d0c6a4e-1b6a-4a39-8a55-0d7f4a1c9e11",
      ...validRequest,
      notes: "Hall 3",
      createdAt: "2026-11-09T09:00:00Z",
      updatedAt: "2026-11-09T09:30:00Z",
    });
    const values = eventToFormValues(detail);
    expect(toEventRequest(values, detail.type)).toEqual({ ...validRequest, notes: "Hall 3" });
  });
});
