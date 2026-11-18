"use client";

import { useTranslations } from "next-intl";
import { useFormContext, useWatch } from "react-hook-form";
import { Loader2 } from "lucide-react";
import { Button } from "@oba/ui/sky/button";
import { TextField, SelectField, FormGrid } from "@oba/shared/features/form";
import {
  getIndustryOptions,
  getCompanySizeOptions,
  getCountryOptions,
} from "@oba/shared/features/organizations/lib/organization-options";
import {
  ONBOARDING_FIELD_MAX_LENGTH,
  type OnboardingStepperFormValues,
} from "../schemas";

interface StepSelfRegisterProps {
  onSubmit: () => void;
  isPending: boolean;
}

export function StepSelfRegister({ onSubmit, isPending }: StepSelfRegisterProps) {
  const t = useTranslations("onboarding");
  const { control, formState } = useFormContext<OnboardingStepperFormValues>();

  const industryOptions = getIndustryOptions(t);
  const companySizeOptions = getCompanySizeOptions(t);
  const countryOptions = getCountryOptions(t);

  const { errors } = formState;
  const [firstName, lastName, name] = useWatch({
    control,
    name: ["firstName", "lastName", "name"],
  });
  const hasRequiredValues =
    firstName.trim().length > 0 &&
    lastName.trim().length > 0 &&
    (name ?? "").trim().length > 0;
  const hasErrors = !!errors.firstName || !!errors.lastName || !!errors.name;

  return (
    <div className="space-y-6">
      {/* Personal Information Section */}
      <div>
        <h2 className="text-foreground mb-4 text-sm font-semibold tracking-wide uppercase">
          {t("personalInfo")}
        </h2>
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
        </FormGrid>
      </div>

      {/* Organization Section */}
      <div>
        <h2 className="text-foreground mb-4 text-sm font-semibold tracking-wide uppercase">
          {t("selfRegister.orgSection")}
        </h2>
        <p className="text-muted-foreground mb-4 text-sm">
          {t("selfRegister.orgSectionDescription")}
        </p>
        <FormGrid className="md:grid-cols-2">
          <div className="md:col-span-2">
            <TextField
              name="name"
              label={t("name")}
              placeholder={t("namePlaceholder")}
              control={control}
              maxLength={ONBOARDING_FIELD_MAX_LENGTH.name}
              required
              disabled={isPending}
            />
          </div>
          <SelectField
            name="industry"
            label={t("industry")}
            placeholder={t("industryPlaceholder")}
            control={control}
            options={industryOptions}
            clearable
            disabled={isPending}
          />
          <SelectField
            name="companySize"
            label={t("companySize")}
            placeholder={t("companySizePlaceholder")}
            control={control}
            options={companySizeOptions}
            clearable
            disabled={isPending}
          />
          <SelectField
            name="country"
            label={t("country")}
            placeholder={t("countryPlaceholder")}
            control={control}
            options={countryOptions}
            clearable
            disabled={isPending}
          />
          <TextField
            name="website"
            label={t("website")}
            placeholder={t("websitePlaceholder")}
            control={control}
            maxLength={ONBOARDING_FIELD_MAX_LENGTH.website}
            disabled={isPending}
          />
        </FormGrid>
      </div>

      <Button
        type="button"
        onClick={onSubmit}
        disabled={!hasRequiredValues || hasErrors || isPending}
        className="h-12 w-full text-base"
      >
        {isPending ? (
          <>
            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
            {t("completing")}
          </>
        ) : (
          t("getStarted")
        )}
      </Button>
    </div>
  );
}
