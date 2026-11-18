import { describe, expect, test } from "bun:test";
import type { UserMe, UserProfile } from "@oba/shared/features/users/schema";

import {
  displayNameOf,
  isProfileDirty,
  PROFILE_NAME_MAX_LENGTH,
  profileFormSchema,
  profileFormValues,
  toProfileUpdate,
} from "@/lib/schemas/users";

const PROFILE = {
  firstName: "Ana",
  lastName: "Kaya",
  phoneNumber: "+31 6 1234 5678",
  userPosition: "Engineer",
  userPreferredLanguage: "nl",
  userGender: "FEMALE",
} as unknown as UserProfile;

describe("toProfileUpdate", () => {
  test("keeps every profile field the form does not edit", () => {
    expect(toProfileUpdate(PROFILE, { firstName: "Anna", lastName: "Kaya" })).toEqual({
      ...PROFILE,
      firstName: "Anna",
      lastName: "Kaya",
    } as never);
  });

  test("saves an emptied name as null and trims the rest", () => {
    const update = toProfileUpdate(PROFILE, { firstName: "  ", lastName: " Yilmaz " });
    expect(update.firstName).toBeNull();
    expect(update.lastName).toBe("Yilmaz");
  });

  test("sends null for the other fields when there is no profile yet", () => {
    expect(toProfileUpdate(null, { firstName: "Ana", lastName: "" })).toEqual({
      phoneNumber: null,
      userPosition: null,
      userPreferredLanguage: null,
      userGender: null,
      firstName: "Ana",
      lastName: null,
    });
  });
});

describe("profileFormSchema", () => {
  test("accepts names up to the backend limit", () => {
    const name: string = "a".repeat(PROFILE_NAME_MAX_LENGTH);
    expect(profileFormSchema.safeParse({ firstName: name, lastName: "" }).success).toBe(true);
  });

  test("rejects a longer name on that field", () => {
    const result = profileFormSchema.safeParse({
      firstName: "Ana",
      lastName: "a".repeat(PROFILE_NAME_MAX_LENGTH + 1),
    });
    expect(result.success).toBe(false);
    expect(result.error?.issues.map((issue) => issue.path[0])).toEqual(["lastName"]);
  });
});

describe("isProfileDirty", () => {
  const saved = { firstName: "Ana", lastName: "Kaya" };

  test("ignores surrounding spaces", () => {
    expect(isProfileDirty(saved, { firstName: " Ana ", lastName: "Kaya" })).toBe(false);
  });

  test("sees a changed name", () => {
    expect(isProfileDirty(saved, { firstName: "Anna", lastName: "Kaya" })).toBe(true);
  });
});

describe("profileFormValues and displayNameOf", () => {
  test("start empty without a profile", () => {
    expect(profileFormValues(undefined)).toEqual({ firstName: "", lastName: "" });
  });

  test("show the full name, else the email", () => {
    const me = { email: "ana@example.com", profile: PROFILE } as unknown as UserMe;
    expect(displayNameOf(me)).toBe("Ana Kaya");
    expect(displayNameOf({ email: "ana@example.com", profile: null } as unknown as UserMe)).toBe(
      "ana@example.com"
    );
    expect(displayNameOf(undefined)).toBe("");
  });
});
