import { describe, expect, test } from "bun:test";

import {
  ACCOUNT_DELETION_OFFERED,
  accountSubtitle,
  isAppearance,
  profileFieldError,
  versionLabel,
} from "@/components/account/accountModel";

describe("ACCOUNT_DELETION_OFFERED", () => {
  test("keeps account deletion turned off until it is offered again", () => {
    expect(ACCOUNT_DELETION_OFFERED).toBe(false);
  });
});

describe("isAppearance", () => {
  test("accepts the three choices", () => {
    expect(isAppearance("system")).toBe(true);
    expect(isAppearance("light")).toBe(true);
    expect(isAppearance("dark")).toBe(true);
  });

  test("rejects anything else, so a corrupt stored value falls back", () => {
    expect(isAppearance("Dark")).toBe(false);
    expect(isAppearance("")).toBe(false);
    expect(isAppearance(null)).toBe(false);
    expect(isAppearance(undefined)).toBe(false);
  });
});

describe("accountSubtitle", () => {
  test("joins the email and the organization", () => {
    expect(accountSubtitle("ana@example.com", "Korur")).toBe("ana@example.com · Korur");
  });

  test("leaves out a missing part", () => {
    expect(accountSubtitle("ana@example.com", null)).toBe("ana@example.com");
    expect(accountSubtitle(undefined, "Korur")).toBe("Korur");
    expect(accountSubtitle(undefined, null)).toBe("");
  });
});

describe("versionLabel", () => {
  test("shows name, version, build and organization", () => {
    expect(versionLabel("Kaizen", "1.0", "24", "Korur")).toBe("Kaizen 1.0 (24) · Korur");
  });

  test("leaves out what the build does not report", () => {
    expect(versionLabel("Kaizen", "1.0", null, null)).toBe("Kaizen 1.0");
    expect(versionLabel("Kaizen", null, null, "Korur")).toBe("Kaizen · Korur");
  });
});

describe("profileFieldError", () => {
  test("reads the nested path the backend reports", () => {
    expect(profileFieldError({ "profile.firstName": "Too long" }, "firstName")).toBe("Too long");
  });

  test("accepts the bare field name", () => {
    expect(profileFieldError({ lastName: "Required" }, "lastName")).toBe("Required");
  });

  test("is null when the field has no error", () => {
    expect(profileFieldError({ "profile.lastName": "Too long" }, "firstName")).toBeNull();
    expect(profileFieldError({}, "lastName")).toBeNull();
  });
});
