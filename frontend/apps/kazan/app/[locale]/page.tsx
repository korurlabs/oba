import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';
import { auth, sanitizeCallbackUrl, signOut } from '@oba/shared/libs/auth';
import { retrySignInOnce } from '@oba/shared/libs/auth/sign-in-failure';
import { displayableErrorCode } from '@oba/shared/libs/auth/sign-in-retry';
import { SignInFailedView } from '@oba/shared/components/sign-in-failed-view';
import { redirect, redirectToAuthApp } from '@oba/shared/libs/i18n/navigation';
import { AuthError } from '@oba/shared/libs/auth/types';
import { Locale } from '@oba/shared/types';
import { redirect as nextRedirect } from 'next/navigation';
import { accountOnboardingUrl, appUrl } from '@oba/shared/constants/apps';
import { readRuntimeConfig } from '@oba/shared/lib/runtime-config';

export const dynamic = 'force-dynamic';

/**
 * Every visit to this route redirects except a failed sign-in, which renders
 * the "Signing in failed" screen; the tab carries that screen's title.
 */
export async function generateMetadata({
  searchParams,
}: {
  searchParams: Promise<{ error?: string }>;
}): Promise<Metadata> {
  const { error } = await searchParams;
  if (!error || error === AuthError.REFRESH_TOKEN_ERROR || error === AuthError.ONBOARDING_REQUIRED) {
    return {};
  }
  const t = await getTranslations('errors.signInFailed');
  return { title: t('title') };
}

/**
 * Sends an unauthenticated/expired-session user to the local direct-Keycloak
 * login route. When the proxy preserved the original deep link as a callbackUrl
 * search param, hand it to the login route so the user returns there after
 * re-auth. We use the plain next/navigation redirect (not the next-intl one)
 * because /api/auth/login must NOT be locale-prefixed — /en/api/... would 404.
 * The caller has already validated callbackUrl (see LocaleHomePage), and the
 * login route sanitizes it again.
 */
function redirectToLogin(locale: Locale, callbackUrl?: string): never {
  if (callbackUrl) {
    nextRedirect(`/api/auth/login?callbackUrl=${encodeURIComponent(callbackUrl)}`);
  }
  return redirectToAuthApp(locale, 'login');
}

/**
 * Sends a user who still has to onboard to the account onboarding page,
 * keeping the validated callbackUrl so account can return the user to it.
 */
function redirectToOnboarding(callbackUrl = '/home'): never {
  nextRedirect(accountOnboardingUrl(callbackUrl, readRuntimeConfig().baseUrl || appUrl('kazan')));
}

/** The screen of a sign-in that failed again after its automatic retry. */
async function SignInFailed({ error, signInUrl }: { error: string; signInUrl: string }) {
  const t = await getTranslations('errors.signInFailed');
  const code = displayableErrorCode(error);
  return (
    <SignInFailedView
      title={t('title')}
      description={t('description')}
      action={t('action')}
      signInUrl={signInUrl}
      detail={code && t('code', { code })}
    />
  );
}

/**
 * Locale Root Page
 *
 * Routes users based on their authentication status and user status.
 * Also handles auth error redirects from the shared proxy middleware and
 * failed sign-ins, which Auth.js sends here with any other `error`.
 * - Failed sign-in, no usable session -> one automatic sign-in, then the
 *   "Signing in failed" screen (see retrySignInOnce)
 * - Auth error or no session -> /api/auth/login (keeps callbackUrl)
 * - Onboarding required -> Account's /onboarding (keeps callbackUrl)
 * - Active + callbackUrl -> callbackUrl
 * - Active, no callbackUrl -> /home
 */
export default async function LocaleHomePage({
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

  // Handle error query parameter from proxy redirect
  if (error === AuthError.REFRESH_TOKEN_ERROR) {
    return redirectToLogin(locale, callbackUrl);
  }

  if (error === AuthError.ONBOARDING_REQUIRED) {
    return redirectToOnboarding(callbackUrl);
  }

  const session = await auth();
  const hasUsableSession = Boolean(session?.user) && session?.error !== AuthError.REFRESH_TOKEN_ERROR;

  // A failed sign-in without a usable session must not start sign-in after
  // sign-in: with the Keycloak SSO session alive, each one fails the same way.
  if (error && !hasUsableSession) {
    const signInUrl = await retrySignInOnce(callbackUrl);
    return <SignInFailed error={error} signInUrl={signInUrl} />;
  }

  // Not authenticated — go to login
  if (!session?.user) {
    return redirectToLogin(locale, callbackUrl);
  }

  // Handle session-level errors
  if (session.error === AuthError.REFRESH_TOKEN_ERROR) {
    return redirectToLogin(locale, callbackUrl);
  }

  // Handle user status
  if (session.userStatus === 'PENDING_ONBOARDING') {
    return redirectToOnboarding(callbackUrl);
  }

  if (session.userStatus === 'DELETED') {
    await signOut({ redirectTo: '/' });
    return;
  }

  // If callbackUrl is provided, redirect to it (local deep link)
  if (callbackUrl) {
    nextRedirect(callbackUrl);
    return;
  }

  // Default: active user goes to Home
  redirect({ href: { pathname: '/home', query: {} }, locale });
}
