import { z } from "zod";
import type { UserMe, UserProfile, UserProfileUpdate } from "@oba/shared/features/users/schema";

/*
 * The user types come from `@oba/shared/features/users/schema`, so the app
 * and Kaizen web parse `GET /users/me` with one contract. This file adds what
 * only the mobile Profile form needs.
 */

/** The longest first or last name `PUT /users/me` accepts. */
export const PROFILE_NAME_MAX_LENGTH = 255;

/** The Profile form: the two names the user may change. Empty is allowed and saved as null. */
export const profileFormSchema = z.object({
  firstName: z.string().trim().max(PROFILE_NAME_MAX_LENGTH),
  lastName: z.string().trim().max(PROFILE_NAME_MAX_LENGTH),
});

export type ProfileFormValues = z.infer<typeof profileFormSchema>;

/** A name left empty is stored as null, not as an empty string. */
function toNullable(value: string): string | null {
  const trimmed: string = value.trim();
  return trimmed === "" ? null : trimmed;
}

/**
 * The profile for `PUT /users/me`: the edited names over every other field of
 * the loaded profile, because the backend replaces the whole profile with the
 * one it receives.
 */
export function toProfileUpdate(
  current: UserProfile | null,
  values: ProfileFormValues
): UserProfileUpdate {
  return {
    phoneNumber: current?.phoneNumber ?? null,
    userPosition: current?.userPosition ?? null,
    userPreferredLanguage: current?.userPreferredLanguage ?? null,
    userGender: current?.userGender ?? null,
    firstName: toNullable(values.firstName),
    lastName: toNullable(values.lastName),
  };
}

/** The form's starting values from the loaded profile. */
export function profileFormValues(me: UserMe | undefined): ProfileFormValues {
  return {
    firstName: me?.profile?.firstName ?? "",
    lastName: me?.profile?.lastName ?? "",
  };
}

/** True when the form differs from the saved profile, ignoring surrounding spaces. */
export function isProfileDirty(saved: ProfileFormValues, edited: ProfileFormValues): boolean {
  return (
    saved.firstName.trim() !== edited.firstName.trim() ||
    saved.lastName.trim() !== edited.lastName.trim()
  );
}

/**
 * The name to show for the user: first and last name, else the email. The
 * same rule picks the avatar initials.
 */
export function displayNameOf(me: Pick<UserMe, "email" | "profile"> | undefined): string {
  const name: string = [me?.profile?.firstName, me?.profile?.lastName]
    .map((part) => part?.trim() ?? "")
    .filter((part) => part !== "")
    .join(" ");
  return name || me?.email || "";
}
