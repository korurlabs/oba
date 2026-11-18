import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { redirect as nextRedirect } from "next/navigation";
import { auth, sanitizeCallbackUrl, signOut } from "@oba/shared/libs/auth";
import { retrySignInOnce } from "@oba/shared/libs/auth/sign-in-failure";
import { displayableErrorCode } from "@oba/shared/libs/auth/sign-in-retry";
import { SignInFailedView } from "@oba/shared/components/sign-in-failed-view";
import { AuthError } from "@oba/shared/libs/auth/types";
import { redirect } from "@oba/shared/libs/i18n/navigation";
import { Locale } from "@oba/shared/types";
import { accountOnboardingUrl, appUrl } from "@oba/shared/constants/apps";

export const dynamic = "force-dynamic";

/** Where a signed-in member lands when no deep link was asked for. */
const HOME_PATH = "/battlefield";

/**
 * A failed sign-in renders this route as the "Signing in failed" screen, so
 * the tab carries its title. Every other outcome of this page is a redirect.
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
  const t = await getTranslations("relations.battlefield");
  return { title: t("title") };
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
  nextRedirect(accountOnboardingUrl(callbackUrl, appUrl("otak")));
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
 * Sign-in gateway
 *
 * `/` is public in the proxy, which sends every session error here with the
 * original page as callbackUrl, so this page routes the user on. Auth.js
 * also sends a failed sign-in here, with any other `error`.
 * - failed sign-in, no usable session -> one automatic sign-in, then the
 *   "Signing in failed" screen (see retrySignInOnce)
 * - auth error or no session -> /api/auth/login (keeps callbackUrl)
 * - onboarding required -> account onboarding (keeps callbackUrl)
 * - active with a callbackUrl -> callbackUrl
 * - active -> the lead battlefield
 */
export default async function GatewayPage({
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

  redirect({ href: { pathname: HOME_PATH, query: {} }, locale });
  return null;
}
