import { describe, expect, test } from "bun:test";
import {
  createIcpFormSchema,
  emptyIcpFormValues,
  icpDetailSchema,
  icpListItemSchema,
  icpQueryRequestSchema,
  icpRequestSchema,
  icpToFormValues,
  toIcpRequest,
  type IcpFormValues,
} from "./index";

const schema = createIcpFormSchema((key: string) => key);

function errorsOf(values: IcpFormValues): Record<string, string> {
  const result = schema.safeParse(values);
  if (result.success) return {};
  return Object.fromEntries(
    result.error.issues.map((issue) => [String(issue.path[0]), issue.message])
  );
}

const valid: IcpFormValues = { ...emptyIcpFormValues(), name: "Textile makers", industry: "OTHER" };

// Field for field the IcpDetailResponse of the API, nulls included.
const detailWithNulls = {
  id: "7d0c6a4e-1b6a-4a39-8a55-0d7f4a1c9e11",
  name: "Textile makers",
  industry: "MANUFACTURING",
  country: null,
  companySizeMin: null,
  companySizeMax: null,
  targetPersonRole: null,
  updatedAt: "2026-11-09T09:30:00Z",
  description: null,
  createdAt: "2026-11-09T09:00:00Z",
} as const;

const detailFull = {
  ...detailWithNulls,
  country: "NL",
  companySizeMin: "SIZE_11_50",
  companySizeMax: "SIZE_201_500",
  targetPersonRole: "CEO",
  description: "Family-owned producers.",
} as const;

describe("ICP form schema", () => {
  test("name and industry are required", () => {
    expect(errorsOf(emptyIcpFormValues())).toEqual({
      name: "errors.nameRequired",
      industry: "errors.industryRequired",
    });
    expect(errorsOf({ ...valid, name: "   " }).name).toBe("errors.nameRequired");
  });

  test("the name is at most 100 characters and the description at most 10000", () => {
    expect(errorsOf({ ...valid, name: "a".repeat(100) })).toEqual({});
    expect(errorsOf({ ...valid, name: "a".repeat(101) }).name).toBe("errors.nameMax");
    expect(errorsOf({ ...valid, description: "a".repeat(10000) })).toEqual({});
    expect(errorsOf({ ...valid, description: "a".repeat(10001) }).description).toBe(
      "errors.descriptionMax"
    );
  });

  test("the minimum size may equal or lie below the maximum", () => {
    expect(
      errorsOf({ ...valid, companySizeMin: "SIZE_11_50", companySizeMax: "SIZE_11_50" })
    ).toEqual({});
    expect(
      errorsOf({ ...valid, companySizeMin: "SIZE_1_10", companySizeMax: "SIZE_5001_PLUS" })
    ).toEqual({});
  });

  test("a minimum above the maximum is refused on the maximum field", () => {
    expect(
      errorsOf({ ...valid, companySizeMin: "SIZE_201_500", companySizeMax: "SIZE_11_50" })
    ).toEqual({ companySizeMax: "errors.sizeRange" });
    expect(
      errorsOf({ ...valid, companySizeMin: "SIZE_5001_PLUS", companySizeMax: "SIZE_1_10" })
        .companySizeMax
    ).toBe("errors.sizeRange");
  });

  test("a range with one side empty passes", () => {
    expect(errorsOf({ ...valid, companySizeMin: "SIZE_5001_PLUS" })).toEqual({});
    expect(errorsOf({ ...valid, companySizeMax: "SIZE_1_10" })).toEqual({});
  });

  test("the country accepts a code or empty and refuses an unknown code", () => {
    expect(errorsOf({ ...valid, country: "NL" })).toEqual({});
    const unknown = "XX" as unknown as IcpFormValues["country"];
    expect(errorsOf({ ...valid, country: unknown }).country).toBeDefined();
  });
});

describe("toIcpRequest", () => {
  test("empty optional fields are sent as null", () => {
    expect(toIcpRequest(valid, "OTHER")).toEqual({
      name: "Textile makers",
      industry: "OTHER",
      country: null,
      companySizeMin: null,
      companySizeMax: null,
      targetPersonRole: null,
      description: null,
    });
  });

  test("a blank description is sent as null, because the server refuses an empty one", () => {
    expect(toIcpRequest({ ...valid, description: "" }, "OTHER").description).toBeNull();
    expect(toIcpRequest({ ...valid, description: " \n\t " }, "OTHER").description).toBeNull();
  });

  test("a description with text is sent as typed", () => {
    expect(toIcpRequest({ ...valid, description: "  Mills  " }, "OTHER").description).toBe(
      "  Mills  "
    );
  });

  test("filled fields are passed through and the request matches its schema", () => {
    const request = toIcpRequest(
      {
        ...valid,
        country: "NL",
        companySizeMin: "SIZE_11_50",
        companySizeMax: "SIZE_201_500",
        targetPersonRole: "CISO",
        description: "Mills",
      },
      "MANUFACTURING"
    );
    expect(request).toEqual({
      name: "Textile makers",
      industry: "MANUFACTURING",
      country: "NL",
      companySizeMin: "SIZE_11_50",
      companySizeMax: "SIZE_201_500",
      targetPersonRole: "CISO",
      description: "Mills",
    });
    expect(icpRequestSchema.safeParse(request).success).toBe(true);
  });
});

describe("ICP response schemas", () => {
  test("a detail with every optional field null parses", () => {
    expect(icpDetailSchema.parse(detailWithNulls)).toEqual(detailWithNulls);
  });

  test("a detail with every field set parses", () => {
    expect(icpDetailSchema.parse(detailFull)).toEqual(detailFull);
  });

  test("a list item has no description and ignores it", () => {
    const { description: _description, createdAt: _createdAt, ...item } = detailFull;
    expect(icpListItemSchema.parse(item)).toEqual(item);
    expect(icpListItemSchema.parse(detailFull)).not.toHaveProperty("description");
  });

  test("an unknown country, industry or size band is refused", () => {
    expect(icpDetailSchema.safeParse({ ...detailFull, country: "XX" }).success).toBe(false);
    expect(icpDetailSchema.safeParse({ ...detailFull, industry: "MINING" }).success).toBe(false);
    expect(icpDetailSchema.safeParse({ ...detailFull, companySizeMin: "HUGE" }).success).toBe(
      false
    );
  });

  test("a missing nullable field is refused", () => {
    const { description: _description, ...withoutDescription } = detailWithNulls;
    expect(icpDetailSchema.safeParse(withoutDescription).success).toBe(false);
  });
});

describe("ICP form mapping", () => {
  test("a stored ICP with nulls maps to empty strings", () => {
    const parsed = icpDetailSchema.parse(detailWithNulls);
    expect(icpToFormValues(parsed)).toEqual({
      name: "Textile makers",
      industry: "MANUFACTURING",
      country: "",
      companySizeMin: "",
      companySizeMax: "",
      targetPersonRole: "",
      description: "",
    });
  });

  test("a stored ICP survives the round trip to a request", () => {
    const parsed = icpDetailSchema.parse(detailFull);
    expect(toIcpRequest(icpToFormValues(parsed), parsed.industry)).toEqual({
      name: parsed.name,
      industry: parsed.industry,
      country: "NL",
      companySizeMin: "SIZE_11_50",
      companySizeMax: "SIZE_201_500",
      targetPersonRole: "CEO",
      description: "Family-owned producers.",
    });
  });
});

describe("icpQueryRequestSchema", () => {
  test("page starts at 1 and amount is 1 to 50", () => {
    expect(icpQueryRequestSchema.safeParse({ page: 1, amount: 25 }).success).toBe(true);
    expect(icpQueryRequestSchema.safeParse({ page: 0, amount: 25 }).success).toBe(false);
    expect(icpQueryRequestSchema.safeParse({ page: 1, amount: 51 }).success).toBe(false);
    expect(icpQueryRequestSchema.safeParse({ page: 1, amount: 0 }).success).toBe(false);
  });
});
