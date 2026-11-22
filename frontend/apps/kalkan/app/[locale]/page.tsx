import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { redirect as nextRedirect } from "next/navigation";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { auth, sanitizeCallbackUrl, signOut } from "@oba/shared/libs/auth";
import { retrySignInOnce } from "@oba/shared/libs/auth/sign-in-failure";
import { displayableErrorCode } from "@oba/shared/libs/auth/sign-in-retry";
import { SignInFailedView } from "@oba/shared/components/sign-in-failed-view";
import { AuthError } from "@oba/shared/libs/auth/types";
import { redirect } from "@oba/shared/libs/i18n/navigation";
import { Locale } from "@oba/shared/types";
import { prefetchMe } from "@oba/shared/features/users/api/prefetch-me";
import { accountOnboardingUrl, appUrl } from "@oba/shared/constants/apps";
import { KalkanAppShell } from "@/components/layout/kalkan-app-shell";
import { OverviewView } from "@/features/overview/components/overview-view";
import { prefetchPageTree } from "@/features/pages/lib/prefetch";

export const dynamic = "force-dynamic";

/**
 * A failed sign-in renders this route as the "Signing in failed" screen, so
 * the tab carries its title. A signed-in user with `error` is redirected to
 * the clean URL, and the onboarding and refresh errors are redirected too, so
 * any other `error` here means the screen.
 */
export async function generateMetadata({
  searchParams,
}: {
  searchParams: Promise<{ error?: string }>;
}): Promise<Metadata> {
  const { error } = await searchParams;
  if (error && error !== AuthError.REFRESH_TOKEN_ERROR && error !== AuthError.ONBOARDING_REQUIRED) {
    const t = await getTranslations("errors.signInFailed");
    return { title: t("title") };
  }
  return {};
}

/**
 * Sends a user without a usable session to the local sign-in route, keeping
 * the deep link the proxy preserved. /api/auth/login must not be
 * locale-prefixed, hence the plain next/navigation redirect.
 */
function redirectToLogin(callbackUrl?: string): never {
  const query = callbackUrl ? `?callbackUrl=${encodeURIComponent(callbackUrl)}` : "";
  nextRedirect(`/api/auth/login${query}`);
}

/**
 * Sends a user who still has to onboard to the account onboarding page,
 * keeping the validated callbackUrl so account can return the user to it.
 * Without one, account returns the user to this gateway.
 */
function redirectToOnboarding(callbackUrl = "/"): never {
  nextRedirect(accountOnboardingUrl(callbackUrl, appUrl("kalkan")));
}

/** The screen of a sign-in that failed again after its automatic retry. */
async function SignInFailed({ error, signInUrl }: { error: string; signInUrl: string }) {
  const t = await getTranslations("errors.signInFailed");
  const code = displayableErrorCode(error);
  return (
    <SignInFailedView
      title={t("title")}
      description={t("description")}
      action={t("action")}
      signInUrl={signInUrl}
      detail={code && t("code", { code })}
    />
  );
}

/**
 * Home page and sign-in gateway
 *
 * `/` is public in the proxy, which sends every session error here with the
 * original page as callbackUrl, so this page routes the user on. Auth.js
 * also sends a failed sign-in here, with any other `error`.
 * - failed sign-in, no usable session -> one automatic sign-in, then the
 *   "Signing in failed" screen (see retrySignInOnce)
 * - auth error or no session -> /api/auth/login (keeps callbackUrl)
 * - onboarding required -> account onboarding (keeps callbackUrl)
 * - active with a callbackUrl -> callbackUrl
 * - active with an `error` -> `/`, so the error leaves the URL
 * - active, no callbackUrl -> the overview inside the Kalkan shell
 *
 * `/` cannot sit in the dashboard group, whose layout sends a visitor without
 * a session straight to sign-in and would break the failed-sign-in handling
 * above, so this page renders the shell itself, as the dashboard layout does:
 * the current-user ("me") query and the page tree prefetched and hydrated
 * around the shell, which reads both, and the session's default organization
 * handed to the shell so the first render keys its queries on the
 * organization they were prefetched under.
 */
export default async function HomePage({
  params,
  searchParams,
}: {
  params: Promise<{ locale: string }>;
  searchParams: Promise<{ error?: string; callbackUrl?: string }>;
}) {
  const locale = (await params).locale as Locale;
  const { error, callbackUrl: rawCallbackUrl } = await searchParams;
  // Validate callbackUrl to prevent open redirect attacks
  const callbackUrl = sanitizeCallbackUrl(rawCallbackUrl);

  if (error === AuthError.REFRESH_TOKEN_ERROR) {
    redirectToLogin(callbackUrl);
  }
  if (error === AuthError.ONBOARDING_REQUIRED) {
    redirectToOnboarding(callbackUrl);
  }

  const session = await auth();
  const hasUsableSession =
    Boolean(session?.user) && session?.error !== AuthError.REFRESH_TOKEN_ERROR;

  // A failed sign-in without a usable session must not start sign-in after
  // sign-in: with the Keycloak SSO session alive, each one fails the same way.
  if (error && !hasUsableSession) {
    const signInUrl = await retrySignInOnce(callbackUrl);
    return <SignInFailed error={error} signInUrl={signInUrl} />;
  }

  if (!session?.user || session.error === AuthError.REFRESH_TOKEN_ERROR) {
    redirectToLogin(callbackUrl);
  }
  if (session.userStatus === "PENDING_ONBOARDING") {
    redirectToOnboarding(callbackUrl);
  }
  if (session.userStatus === "DELETED") {
    await signOut({ redirectTo: "/" });
    return null;
  }
  if (callbackUrl) {
    nextRedirect(callbackUrl);
  }
  // A signed-in user who opened a failed sign-in's `?error=` URL sees the
  // home page under its clean URL, so a reload or a shared link carries no error.
  if (error) {
    redirect({ href: { pathname: "/", query: {} }, locale });
  }

  const queryClient = makeQueryClient();
  // Best-effort: on a failed prefetch the client fetches through useGetMe.
  await prefetchMe(queryClient, session.accessToken, "home-page");
  const organizationId = session.user.defaultOrganizationId ?? null;
  if (organizationId) {
    await prefetchPageTree(queryClient, organizationId, "home-page");
  }

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <KalkanAppShell organizationId={organizationId}>
        <OverviewView />
      </KalkanAppShell>
    </HydrationBoundary>
  );
}
