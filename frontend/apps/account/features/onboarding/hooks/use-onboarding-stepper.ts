"use client";

import { useState, useCallback, useEffect, useRef } from "react";
import { useSession } from "next-auth/react";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import type { UseFormReturn } from "react-hook-form";
import {
  useCompleteOnboarding,
  useSubmitInvitationDecisions,
} from "./use-onboarding";
import { useRemindLater } from "@/features/pending-invitations/hooks/use-pending-invitations";
import type { InvitationDecision } from "@/features/pending-invitations/schemas";
import type {
  OnboardingStepperFormValues,
  OnboardingCompleteRequest,
} from "../schemas";

// How long a session refresh waits for a session update that is already
// running: 50 checks, 100 ms apart.
const SESSION_UPDATE_POLL_MS = 100;
const SESSION_UPDATE_MAX_POLLS = 50;

interface UseOnboardingStepperOptions {
  form: UseFormReturn<OnboardingStepperFormValues>;
  callbackUrl: string;
  hasInvitations: boolean;
}

export function useOnboardingStepper({
  form,
  callbackUrl,
  hasInvitations,
}: UseOnboardingStepperOptions) {
  const t = useTranslations("onboarding");
  const { update } = useSession();
  const completeOnboarding = useCompleteOnboarding();
  const submitInvitationDecisions = useSubmitInvitationDecisions();
  const remindLaterMutation = useRemindLater();

  const [currentStep, setCurrentStep] = useState<1 | 2>(1);
  const [decisions, setDecisions] = useState<Map<string, InvitationDecision>>(new Map());
  const [remindedIds, setRemindedIds] = useState<Set<string>>(new Set());
  const [showOrgSection, setShowOrgSection] = useState(false);
  const [isPending, setIsPending] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  // Set once onboarding and the invitation decisions were sent in this mount.
  // A resubmit after a failed session refresh then repeats only the refresh:
  // posting `onboarding/complete` again for an active user would fail.
  const isOnboardingSavedRef = useRef(false);
  // The `update` of the latest render. Each `update` closes over the
  // provider's loading flag of the render it was created in, so the one a
  // running submit captured keeps answering "busy" after that update ended.
  const updateRef = useRef(update);
  useEffect(() => {
    updateRef.current = update;
  }, [update]);

  // Refreshes the session and resolves it, or null when that failed.
  // `update()` does not throw: it resolves null when the session request
  // fails and undefined, at once, when another update is in flight. A running
  // update is waited for and the refresh is then asked again, because that
  // update may have read the token before onboarding was saved. A failed
  // request is retried once.
  const refreshSession = useCallback(async () => {
    let hasFailed = false;
    for (let polls = 0; polls <= SESSION_UPDATE_MAX_POLLS; ) {
      const session = await updateRef.current({});
      if (session) return session;

      if (session === null) {
        if (hasFailed) return null;
        hasFailed = true;
        continue;
      }

      polls += 1;
      await new Promise((resolve) => setTimeout(resolve, SESSION_UPDATE_POLL_MS));
    }
    return null;
  }, []);

  const goToStep2 = useCallback(async () => {
    const valid = await form.trigger(["firstName", "lastName", "jobTitle"]);
    if (valid) {
      setCurrentStep(2);
    }
  }, [form]);

  const goToStep1 = useCallback(() => {
    if (!isPending) {
      setCurrentStep(1);
    }
  }, [isPending]);

  const setDecision = useCallback(
    (id: string, decision: InvitationDecision | null) => {
      setDecisions((prev) => {
        const next = new Map(prev);
        if (decision === null) {
          next.delete(id);
        } else {
          next.set(id, decision);
        }
        return next;
      });
    },
    [],
  );

  const toggleOrgSection = useCallback(() => {
    setShowOrgSection((prev) => !prev);
  }, []);

  const handleRemindLater = useCallback(
    (id: string) => {
      remindLaterMutation.mutate(id, {
        onSuccess: () => {
          setRemindedIds((prev) => new Set(prev).add(id));
          // Also remove any existing decision for this invitation
          setDecisions((prev) => {
            const next = new Map(prev);
            next.delete(id);
            return next;
          });
        },
      });
    },
    [remindLaterMutation],
  );

  const handleSubmit = useCallback(async () => {
    if (!isOnboardingSavedRef.current) {
      // The submit buttons are plain buttons, so the resolver has to be run
      // here, for exactly the fields this path sends.
      const sendsOrganization = !hasInvitations || showOrgSection;
      const isValid = await form.trigger(
        sendsOrganization
          ? [
              "firstName",
              "lastName",
              "jobTitle",
              "name",
              "industry",
              "companySize",
              "country",
              "website",
            ]
          : ["firstName", "lastName", "jobTitle"],
      );
      if (!isValid) return;
    }

    setIsPending(true);
    setErrorMessage(null);

    if (!isOnboardingSavedRef.current) {
      const values = form.getValues();

      // Build onboarding request
      const onboardingData: OnboardingCompleteRequest = {
        firstName: values.firstName.trim(),
        lastName: values.lastName.trim(),
        jobTitle: values.jobTitle?.trim() || undefined,
        name: undefined,
        industry: undefined,
        companySize: undefined,
        country: undefined,
        website: undefined,
      };

      // Include org fields if self-register path OR org section is expanded with a name
      if (!hasInvitations) {
        // Self-register path: org name is required (enforced by the schema)
        onboardingData.name = values.name?.trim() || undefined;
        onboardingData.industry = values.industry || undefined;
        onboardingData.companySize = values.companySize || undefined;
        onboardingData.country = values.country || undefined;
        onboardingData.website = values.website?.trim() || undefined;
      } else if (showOrgSection && values.name?.trim()) {
        // Invited path with org creation
        onboardingData.name = values.name.trim();
        onboardingData.industry = values.industry || undefined;
        onboardingData.companySize = values.companySize || undefined;
        onboardingData.country = values.country || undefined;
        onboardingData.website = values.website?.trim() || undefined;
      }

      try {
        // Step 1: Complete onboarding
        await completeOnboarding.mutateAsync(onboardingData);
      } catch (error) {
        console.error("[Onboarding] completeOnboarding failed:", error);
        setErrorMessage(t("error"));
        setIsPending(false);
        return;
      }

      // Step 2: Submit invitation decisions (only for invited path)
      if (hasInvitations && decisions.size > 0) {
        const failedInvitationIds = await submitInvitationDecisions.mutateAsync(
          Array.from(decisions.entries()),
        );

        if (failedInvitationIds.length > 0) {
          toast.error(t("invitationDecisionPartialError"));
        }
      }

      isOnboardingSavedRef.current = true;
    }

    // Refresh the session, then redirect. Without a refreshed token the
    // dashboard still sees PENDING_ONBOARDING and sends the user back here,
    // so a failed refresh is reported instead of navigating.
    const session = await refreshSession();
    if (!session) {
      console.error("[Onboarding] session refresh before redirect failed");
      setErrorMessage(t("error"));
      setIsPending(false);
      return;
    }

    // The page is being left: stay pending until unload so the button cannot
    // be clicked a second time.
    window.location.href = callbackUrl;
  }, [
    form,
    hasInvitations,
    showOrgSection,
    decisions,
    completeOnboarding,
    submitInvitationDecisions,
    refreshSession,
    callbackUrl,
    t,
  ]);

  return {
    currentStep,
    goToStep2,
    goToStep1,
    decisions,
    setDecision,
    remindedIds,
    handleRemindLater,
    showOrgSection,
    toggleOrgSection,
    handleSubmit,
    isPending,
    isError: !!errorMessage,
    errorMessage,
  };
}
