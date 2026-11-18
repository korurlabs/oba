"use client";

import { useSession } from "next-auth/react";
import Image from "next/image";
import { useSearchParams } from "next/navigation";
import { useEffect, useMemo } from "react";
import { useTranslations } from "next-intl";
import { useForm, FormProvider } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { sanitizeCallbackUrl } from "@oba/shared/libs/auth/callback-url";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import type { InvitationResponse } from "@oba/shared/features/users/schema";
import {
  createOnboardingStepperSchema,
  type OnboardingStepperFormValues,
} from "../schemas";
import { useOnboardingStepper } from "../hooks/use-onboarding-stepper";
import { OnboardingStepperHeader } from "./onboarding-stepper-header";
import { OnboardingSkeleton } from "./onboarding-skeleton";
import { StepPersonalInfo } from "./onboarding-step-personal-info";
import { StepSelfRegister } from "./onboarding-step-self-register";
import { StepInvitations } from "./onboarding-step-invitations";

const DEFAULT_CALLBACK_URL = "/";

export function OnboardingStepper() {
  const t = useTranslations("onboarding");
  const tOrganization = useTranslations("organization");
  const { data: session, status } = useSession();
  const searchParams = useSearchParams();

  const callbackUrl = useMemo(
    () =>
      sanitizeCallbackUrl(searchParams.get("callbackUrl")) ??
      DEFAULT_CALLBACK_URL,
    [searchParams],
  );

  // Fetch user data to get pending invitations
  const { data: meResponse, isLoading: isMeLoading } = useGetMe();

  const pendingInvitations: InvitationResponse[] = useMemo(
    () =>
      (meResponse?.data?.pendingInvitations ?? []).filter(
        (inv) => inv.status === "PENDING",
      ),
    [meResponse],
  );

  const hasInvitations = pendingInvitations.length > 0;

  // Single form for the entire stepper. The organization name is required
  // only on the self-register path; an invited user may skip the organization.
  const schema = useMemo(
    () =>
      createOnboardingStepperSchema(t, {
        organizationNameRequiredMessage: hasInvitations
          ? undefined
          : tOrganization("nameRequired"),
      }),
    [t, tOrganization, hasInvitations],
  );
  const form = useForm<OnboardingStepperFormValues>({
    resolver: zodResolver(schema),
    mode: "onChange",
    defaultValues: {
      firstName: "",
      lastName: "",
      jobTitle: "",
      name: "",
      industry: "",
      companySize: "",
      country: "",
      website: "",
    },
  });

  const stepper = useOnboardingStepper({
    form,
    callbackUrl,
    hasInvitations,
  });

  // Auth guards
  useEffect(() => {
    if (status === "unauthenticated") {
      // Use a hard navigation to the local Keycloak login route. next-intl's
      // router would locale-prefix /api (/en/api/auth/login), which the proxy
      // matcher excludes and would 404. Preserve the callbackUrl so the user
      // returns to their onboarding destination after authenticating.
      window.location.href = `/api/auth/login?callbackUrl=${encodeURIComponent(callbackUrl)}`;
      return;
    }

    if (status === "authenticated" && session?.userStatus === "ACTIVE") {
      window.location.href = callbackUrl;
    }
  }, [status, session?.userStatus, callbackUrl]);

  // Show the skeleton only while there is nothing to render from: neither a
  // session nor the me data the page prefetched. The session provider starts
  // without a session, so waiting for it alone would flash the skeleton on
  // every load. next-auth also reports "loading" during every `update()`,
  // when the session is already there.
  if ((status === "loading" && !session && !meResponse) || isMeLoading) {
    return <OnboardingSkeleton />;
  }

  return (
    <div className="animate-fade-in bg-card relative w-full max-w-4xl rounded-3xl p-8 shadow-xl sm:p-12">
      {/* Logo */}
      <div className="mb-6 flex justify-center">
        <Image
          src="/assets/korur-mark.png"
          alt=""
          width={72}
          height={72}
          priority
          className="rounded-[20px] bg-white"
        />
      </div>

      {/* Header */}
      <div className="mb-8 text-center">
        <h1 className="text-foreground mb-2 text-3xl font-bold tracking-tight">
          {t("title")}
        </h1>
        <p className="text-muted-foreground text-base">{t("description")}</p>
      </div>

      {/* Error Alert */}
      {stepper.isError && (
        <div className="bg-error border-error/20 text-error-foreground mb-6 rounded-xl border p-4 text-sm">
          {stepper.errorMessage}
        </div>
      )}

      {/* Form Provider wrapping all steps */}
      <FormProvider {...form}>
        {hasInvitations ? (
          <>
            {/* Multi-step stepper for invited users */}
            <OnboardingStepperHeader
              currentStep={stepper.currentStep}
              onStepClick={stepper.goToStep1}
              isPending={stepper.isPending}
            />

            {stepper.currentStep === 1 && (
              <StepPersonalInfo
                onNext={stepper.goToStep2}
                isPending={stepper.isPending}
              />
            )}

            {stepper.currentStep === 2 && (
              <StepInvitations
                invitations={pendingInvitations}
                decisions={stepper.decisions}
                onDecision={stepper.setDecision}
                remindedIds={stepper.remindedIds}
                onRemindLater={stepper.handleRemindLater}
                showOrgSection={stepper.showOrgSection}
                onToggleOrgSection={stepper.toggleOrgSection}
                onBack={stepper.goToStep1}
                onComplete={stepper.handleSubmit}
                isPending={stepper.isPending}
              />
            )}
          </>
        ) : (
          /* Single-step for self-registered users */
          <StepSelfRegister
            onSubmit={stepper.handleSubmit}
            isPending={stepper.isPending}
          />
        )}
      </FormProvider>
    </div>
  );
}
