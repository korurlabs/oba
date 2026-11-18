"use client";

import { useTranslations } from "next-intl";
import { useFormContext, useWatch } from "react-hook-form";
import { Button } from "@oba/ui/sky/button";
import { TextField, FormGrid } from "@oba/shared/features/form";
import {
  ONBOARDING_FIELD_MAX_LENGTH,
  type OnboardingStepperFormValues,
} from "../schemas";

interface StepPersonalInfoProps {
  onNext: () => void;
  isPending: boolean;
}

export function StepPersonalInfo({ onNext, isPending }: StepPersonalInfoProps) {
  const t = useTranslations("onboarding");
  const { control, formState } = useFormContext<OnboardingStepperFormValues>();

  const firstNameError = formState.errors.firstName;
  const lastNameError = formState.errors.lastName;
  const [firstName, lastName] = useWatch({
    control,
    name: ["firstName", "lastName"],
  });
  const hasRequiredValues =
    firstName.trim().length > 0 && lastName.trim().length > 0;
  const hasErrors =
    !!firstNameError || !!lastNameError || !!formState.errors.jobTitle;

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-foreground mb-1 text-lg font-semibold">
          {t("stepPersonalInfo")}
        </h2>
        <p className="text-muted-foreground text-sm">
          {t("stepPersonalInfoDescription")}
        </p>
      </div>

      <FormGrid className="md:grid-cols-2">
        <TextField
          name="firstName"
          label={t("firstName")}
          placeholder={t("firstNamePlaceholder")}
          control={control}
          maxLength={ONBOARDING_FIELD_MAX_LENGTH.firstName}
          required
          disabled={isPending}
        />
        <TextField
          name="lastName"
          label={t("lastName")}
          placeholder={t("lastNamePlaceholder")}
          control={control}
          maxLength={ONBOARDING_FIELD_MAX_LENGTH.lastName}
          required
          disabled={isPending}
        />
        <div className="md:col-span-2">
          <TextField
            name="jobTitle"
            label={t("jobTitle")}
            placeholder={t("jobTitlePlaceholder")}
            control={control}
            maxLength={ONBOARDING_FIELD_MAX_LENGTH.jobTitle}
            disabled={isPending}
          />
        </div>
      </FormGrid>

      <div className="flex justify-end">
        <Button
          type="button"
          onClick={onNext}
          disabled={!hasRequiredValues || hasErrors || isPending}
          className="h-12 px-8 text-base"
        >
          {t("nextStep")}
        </Button>
      </div>
    </div>
  );
}
