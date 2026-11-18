"use client";

import { useTranslations } from "next-intl";
import { Check } from "lucide-react";
import {
  Stepper,
  StepperNav,
  StepperItem,
  StepperTrigger,
  StepperIndicator,
  StepperSeparator,
  StepperTitle,
} from "@oba/ui/components/stepper";

interface OnboardingStepperHeaderProps {
  currentStep: number;
  onStepClick: () => void;
  isPending: boolean;
}

export function OnboardingStepperHeader({
  currentStep,
  onStepClick,
  isPending,
}: OnboardingStepperHeaderProps) {
  const t = useTranslations("onboarding");

  return (
    <Stepper
      value={currentStep}
      onValueChange={(step) => {
        if (!isPending && step < currentStep) {
          onStepClick();
        }
      }}
      indicators={{ completed: <Check className="h-4 w-4" /> }}
    >
      <StepperNav className="mb-8">
        <StepperItem step={1} disabled={isPending}>
          <StepperTrigger className="gap-2">
            <StepperIndicator  />
            <StepperTitle>{t("stepPersonalInfo")}</StepperTitle>
          </StepperTrigger>
          <StepperSeparator />
        </StepperItem>
        <StepperItem step={2} disabled={isPending || currentStep < 2}>
          <StepperTrigger className="gap-2">
            <StepperIndicator />
            <StepperTitle>{t("stepInvitations")}</StepperTitle>
          </StepperTrigger>
        </StepperItem>
      </StepperNav>
    </Stepper>
  );
}
