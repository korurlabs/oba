import { Suspense } from "react";
import { redirect as nextRedirect } from "next/navigation";
import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { auth, sanitizeCallbackUrl } from "@oba/shared/libs/auth";
import { AuthError } from "@oba/shared/libs/auth/types";
import { redirect } from "@oba/shared/libs/i18n/navigation";
import { Locale } from "@oba/shared/types";
import { OnboardingSkeleton } from "@/features/onboarding/components/onboarding-skeleton";
import { OnboardingStepper } from "@/features/onboarding/components/onboarding-stepper";
import { prefetchMe } from "@oba/shared/features/users/api/prefetch-me";

/**
 * Onboarding Page
 *
 * Shown to users with PENDING_ONBOARDING status.
 * - Self-registered users (no invitations): single-step form
 * - Invited users: multi-step stepper with invitation decisions
 *
 * The session gate runs here and not in the `(auth)` layout, which is where
 * Account gates otherwise: both redirects carry the `callbackUrl` search
 * param, and a layout does not receive search params. OnboardingStepper keeps
 * the same two redirects on the client for a session that changes while the
 * page is open.
 *
 * Server-prefetches the current-user ("me") query, which OnboardingStepper
 * reads on mount for the pending invitations. The Suspense boundary is
 * required by the stepper's useSearchParams.
 */
export default async function OnboardingPage({
  params,
  searchParams,
}: {
  params: Promise<{ locale: string }>;
  searchParams: Promise<{ callbackUrl?: string }>;
}) {
  const locale = (await params).locale as Locale;
  const callbackUrl = sanitizeCallbackUrl((await searchParams).callbackUrl);
  const session = await auth();

  // No usable session: sign in first. /api/auth/login must not be
  // locale-prefixed, hence the plain next/navigation redirect.
  if (!session?.user || session.error === AuthError.REFRESH_TOKEN_ERROR) {
    nextRedirect(
      `/api/auth/login?callbackUrl=${encodeURIComponent(callbackUrl ?? "/")}`,
    );
  }

  // Already onboarded: nothing to do here.
  if (session.userStatus === "ACTIVE") {
    if (callbackUrl) {
      nextRedirect(callbackUrl);
    }
    redirect({ href: { pathname: "/", query: {} }, locale });
  }

  const queryClient = makeQueryClient();
  await prefetchMe(queryClient, session.accessToken, "onboarding-page");

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <Suspense fallback={<OnboardingSkeleton />}>
        <OnboardingStepper />
      </Suspense>
    </HydrationBoundary>
  );
}
