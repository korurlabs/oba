"use client";

import { useFormContext } from "react-hook-form";
import { useTranslations } from "next-intl";
import {
  FormGrid,
  TextField,
  ObaForm,
} from "@oba/shared/features/form";
import type { UserProfile } from "@oba/shared/features/users/schema";
import { Button } from "@oba/ui/sky/button";
import { useUpdateProfile } from "../hooks/use-update-profile";
import {
  PROFILE_FIELD_MAX_LENGTH,
  profileFormSchema,
  type ProfileFormValues,
} from "../schemas";

interface ProfileFormProps {
  profile: UserProfile | null;
}

/**
 * Edit form for first name, last name and phone number.
 *
 * The phone number is a plain `TextField`: the shared `PhoneField` works on a
 * `{ country, dialCode, phoneNumber }` object, which does not map onto the
 * backend's single string.
 */
export function ProfileForm({ profile }: ProfileFormProps) {
  const updateProfile = useUpdateProfile();

  // A failed save does not change `values`, so the form keeps the user's
  // input. After a successful save the refetched profile resets it.
  return (
    <ObaForm
      schema={profileFormSchema}
      onSubmit={(data) => updateProfile.mutate(data)}
      values={{
        firstName: profile?.firstName ?? "",
        lastName: profile?.lastName ?? "",
        phoneNumber: profile?.phoneNumber ?? "",
      }}
      className="space-y-6"
    >
      <ProfileFormFields isPending={updateProfile.isPending} />
    </ObaForm>
  );
}

function ProfileFormFields({ isPending }: { isPending: boolean }) {
  const t = useTranslations("profile");
  const { control, formState } = useFormContext<ProfileFormValues>();
  const { isDirty } = formState;

  return (
    <>
      <FormGrid className="md:grid-cols-2">
        <TextField
          name="firstName"
          label={t("firstName")}
          control={control}
          maxLength={PROFILE_FIELD_MAX_LENGTH.firstName}
          autoComplete="given-name"
          disabled={isPending}
        />
        <TextField
          name="lastName"
          label={t("lastName")}
          control={control}
          maxLength={PROFILE_FIELD_MAX_LENGTH.lastName}
          autoComplete="family-name"
          disabled={isPending}
        />
        <TextField
          name="phoneNumber"
          label={t("phoneNumber")}
          control={control}
          type="tel"
          maxLength={PROFILE_FIELD_MAX_LENGTH.phoneNumber}
          autoComplete="tel"
          disabled={isPending}
        />
      </FormGrid>

      <Button type="submit" disabled={isPending || !isDirty}>
        {isPending ? t("saving") : t("save")}
      </Button>
    </>
  );
}
