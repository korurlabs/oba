import { describe, expect, test } from "bun:test";
import { parseAzureLastError } from "./azure-last-error";

describe("parseAzureLastError", () => {
  test("is null without an error", () => {
    expect(parseAzureLastError(null)).toBeNull();
    expect(parseAzureLastError("")).toBeNull();
  });

  test("reads a code without an Entra code", () => {
    expect(parseAzureLastError("AZR_UPS_006")).toEqual({
      code: "AZR_UPS_006",
      entraCode: null,
      known: true,
    });
  });

  test("splits the Entra code from a refused token exchange", () => {
    expect(parseAzureLastError("AZR_UPS_009:AADSTS700213")).toEqual({
      code: "AZR_UPS_009",
      entraCode: "AADSTS700213",
      known: true,
    });
  });

  test("marks a code without its own explanation as unknown", () => {
    expect(parseAzureLastError("OID_NOT_001")).toEqual({
      code: "OID_NOT_001",
      entraCode: null,
      known: false,
    });
  });
});
