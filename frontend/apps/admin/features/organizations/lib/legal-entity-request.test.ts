import { describe, expect, test } from "bun:test";
import type { LegalEntityDetail, LegalEntityFormValues } from "../schemas";
import { toLegalEntityFormValues, toLegalEntityRequest } from "./legal-entity-request";

const filled: LegalEntityFormValues = {
  shortName: "  Korur NL ",
  legalName: " Korur B.V. ",
  country: "NLD",
  registrationNumber: " 12345678 ",
  vatNumber: "NL123456789B01",
  street: "Main street 1",
  postalCode: "1234 AB",
  city: "Amsterdam",
  governingLaw: "Dutch law",
  court: "District Court of Amsterdam",
};

describe("toLegalEntityRequest", () => {
  test("trims every value", () => {
    const request = toLegalEntityRequest(filled);

    expect(request.shortName).toBe("Korur NL");
    expect(request.legalName).toBe("Korur B.V.");
    expect(request.registrationNumber).toBe("12345678");
  });

  test("sends a blank optional field as null and keeps every key, for the full replace", () => {
    const request = toLegalEntityRequest({
      ...filled,
      registrationNumber: "",
      vatNumber: "   ",
      street: "",
      postalCode: "",
      city: "",
      governingLaw: "",
      court: "",
    });

    expect(request).toEqual({
      shortName: "Korur NL",
      legalName: "Korur B.V.",
      country: "NLD",
      registrationNumber: null,
      vatNumber: null,
      street: null,
      postalCode: null,
      city: null,
      governingLaw: null,
      court: null,
    });
  });
});

describe("toLegalEntityFormValues", () => {
  test("an empty form without a legal entity", () => {
    const values = toLegalEntityFormValues(null);

    expect(Object.values(values).every((value) => value === "")).toBe(true);
  });

  test("a stored null becomes an empty field", () => {
    const entity: LegalEntityDetail = {
      id: "7d1f4a0e-6a3b-4c0e-9a55-2f0c1b8d9e10",
      shortName: "Korur NL",
      legalName: "Korur B.V.",
      country: "NLD",
      registrationNumber: null,
      vatNumber: "NL1",
      street: null,
      postalCode: null,
      city: null,
      governingLaw: null,
      court: null,
      createdAt: "2026-11-20T09:00:00Z",
      updatedAt: "2026-11-21T09:00:00Z",
    };

    const values = toLegalEntityFormValues(entity);

    expect(values.shortName).toBe("Korur NL");
    expect(values.vatNumber).toBe("NL1");
    expect(values.registrationNumber).toBe("");
    expect(values.court).toBe("");
  });
});
