"use client";

import { useTranslations } from "next-intl";
import { useFormContext } from "react-hook-form";
import { ChevronDown } from "lucide-react";
import { Button } from "@oba/ui/sky/button";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@oba/ui/components/collapsible";
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
import { cn } from "@oba/ui/lib/utils";

interface StepCreateOrgSectionProps {
  open: boolean;
  onToggle: () => void;
  isPending: boolean;
}

export function StepCreateOrgSection({
  open,
  onToggle,
  isPending,
}: StepCreateOrgSectionProps) {
  const t = useTranslations("onboarding");
  const { control } = useFormContext<OnboardingStepperFormValues>();

  const industryOptions = getIndustryOptions(t);
  const companySizeOptions = getCompanySizeOptions(t);
  const countryOptions = getCountryOptions(t);

  return (
    <Collapsible open={open} onOpenChange={onToggle}>
      <CollapsibleTrigger asChild>
        <Button
          type="button"
          variant="ghost"
          className="min-h-14 flex w-full items-center justify-between px-4 py-3"
          disabled={isPending}
        >
          <div className="text-left">
            <p className="text-sm font-medium">{t("createOrgSection")}</p>
            <p className="text-muted-foreground text-xs">
              {t("createOrgSectionDescription")}
            </p>
          </div>
          <ChevronDown
            className={cn(
              "text-muted-foreground h-4 w-4 shrink-0 transition-transform duration-200",
              open && "rotate-180",
            )}
          />
        </Button>
      </CollapsibleTrigger>
      <CollapsibleContent>
        <div className="space-y-4 px-4 pb-4 pt-2">
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
      </CollapsibleContent>
    </Collapsible>
  );
}
