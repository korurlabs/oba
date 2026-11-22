import { describe, expect, test } from "bun:test";
import { LEGAL_ENTITY_LIMITS, legalEntityFormSchema, legalEntityListItemSchema } from "./index";

const schema = legalEntityFormSchema((key) => key);

const valid = {
  shortName: "Korur NL",
  legalName: "Korur B.V.",
  country: "NLD",
  registrationNumber: "",
  vatNumber: "",
  street: "",
  postalCode: "",
  city: "",
  governingLaw: "",
  court: "",
};

function messageOf(values: Record<string, string>, field: string): string | undefined {
  const result = schema.safeParse(values);
  return result.success
    ? undefined
    : result.error.issues.find((issue) => issue.path[0] === field)?.message;
}

describe("legalEntityFormSchema", () => {
  test("accepts the required fields with every optional field empty", () => {
    expect(schema.safeParse(valid).success).toBe(true);
  });

  test.each(["shortName", "legalName", "country"])("refuses a blank %s", (field) => {
    expect(messageOf({ ...valid, [field]: "   " }, field)).toBe(`legalEntities.${field}Required`);
  });

  test.each(Object.entries(LEGAL_ENTITY_LIMITS))(
    "refuses %s longer than %i characters, as the backend does",
    (field, max) => {
      const required = ["shortName", "legalName", "country"].includes(field);
      expect(messageOf({ ...valid, [field]: "x".repeat(max) }, field)).toBeUndefined();
      expect(messageOf({ ...valid, [field]: "x".repeat(max + 1) }, field)).toBe(
        `legalEntities.${field}MaxLength`
      );
      expect(required || messageOf({ ...valid, [field]: "" }, field) === undefined).toBe(true);
    }
  );

  test("checks the length of the trimmed value, which is what is sent", () => {
    expect(messageOf({ ...valid, country: "  NLD  " }, "country")).toBeUndefined();
  });
});

describe("legalEntityListItemSchema", () => {
  test("reads a list item with a null registration number", () => {
    const result = legalEntityListItemSchema.safeParse({
      id: "7d1f4a0e-6a3b-4c0e-9a55-2f0c1b8d9e10",
      shortName: "Korur NL",
      legalName: "Korur B.V.",
      country: "NLD",
      registrationNumber: null,
      updatedAt: "2026-11-21T09:00:00Z",
    });

    expect(result.success).toBe(true);
  });
});
