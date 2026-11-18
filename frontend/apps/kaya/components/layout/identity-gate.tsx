"use client";

import { useQuery } from "@tanstack/react-query";
import { useSession } from "next-auth/react";
import { usePathname, useSearchParams } from "next/navigation";
import { useEffect, useSyncExternalStore, type ReactNode } from "react";
import { AlertTriangle, LogIn, LogOut, ShieldOff, UserCog, UserX } from "lucide-react";

import { Button } from "@oba/ui/sky/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@oba/ui/sky/card";
import { ACCOUNT_LINKS } from "@oba/shared/constants/apps";
import { accessLoss, accessLossOf, subscribeToAccessLoss, type AccessLoss } from "@/lib/api/access";
import { submitLogout } from "@/lib/auth/logout-client";
import { SESSION_REJECTED, signInUrl } from "@/lib/auth/reauth";
import { syncMe } from "@/lib/identity/api";
import { IdentityProvider } from "@/lib/identity/identity-context";

import { KorurLogo } from "./korur-logo";

export const IDENTITY_KEYS = {
  me: ["identity", "me"] as const,
};

/**
 * Lets the dashboard render only for a signed-in member of an organization.
 *
 * `proxy.ts` sends a signed-out visitor to the Keycloak sign-in before the
 * page renders; the gate does the same when the session ends or the API
 * answers 401 while a page is open. That sign-in is forced: the session still
 * looks valid to the sign-in route, which would otherwise send the browser
 * straight back here to be refused again. If the renewed session is refused
 * too, the route lands on `?error=SessionRejected`, shown with a sign-out and
 * sign-in button instead of another redirect. Then the platform's
 * `POST /users/me` creates or refreshes the user and lists its organizations;
 * a user with none Kaya may open (a guest membership does not count) sees
 * "You are not a member of an organization on Kaya". That user stays signed
 * in: the session is shared by every Oba app, so ending it here would also
 * sign the user out of Kaizen, where they may still belong to organizations.
 * A member to whom no organization grants Kaya sees "Kaya is not open to you"
 * instead, and so does a user whose call is refused because Kaya is no longer
 * granted in the organization open. A disabled user, refused by every Oba app, sees "Your account is disabled"
 * with a sign-out button. A later call that reports a lost membership, or a 401, replaces the dashboard
 * the same way.
 */
export function IdentityGate({ children }: { children: ReactNode }) {
  const { data: session, status } = useSession();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const signInError = searchParams.get("error");
  const query = searchParams.toString();
  const here = query === "" ? pathname : `${pathname}?${query}`;
  const signedIn = status === "authenticated" && session.error === undefined;
  const lost = useSyncExternalStore(subscribeToAccessLoss, accessLoss, () => null);

  const me = useQuery({
    queryKey: IDENTITY_KEYS.me,
    queryFn: syncMe,
    enabled: signedIn,
    staleTime: Infinity,
  });

  const loss: AccessLoss | null = lost ?? accessLossOf(me.error);
  const noApp =
    loss === "no-app" ||
    (me.data !== undefined && me.data.memberships.length === 0 && me.data.kayaWithheld);
  const notMember =
    loss === "not-member" || (me.data !== undefined && me.data.memberships.length === 0);
  const disabled = loss === "disabled" || me.data?.userStatus === "DISABLED";
  // A refused user stays on the refusal page, even once the session has ended.
  const refused = noApp || notMember || disabled;
  const mustSignIn =
    !refused &&
    (status === "unauthenticated" ||
      (status === "authenticated" && session.error !== undefined) ||
      loss === "signed-out");
  const redirecting = mustSignIn && signInError === null;

  useEffect(() => {
    if (redirecting) {
      window.location.assign(signInUrl(here, true));
    }
  }, [redirecting, here]);

  if (mustSignIn && signInError === SESSION_REJECTED) {
    return <SessionRejected />;
  }
  if (mustSignIn && signInError !== null) {
    return <SignInFailed reason={signInError} />;
  }
  if (disabled) {
    return <Disabled />;
  }
  if (noApp) {
    return <NoAppAccess />;
  }
  if (notMember) {
    return <NotMember />;
  }
  if (me.error !== null && !redirecting) {
    return <IdentityError message={me.error.message} onRetry={() => void me.refetch()} />;
  }
  if (me.data === undefined || redirecting) {
    return <Splash />;
  }
  return <IdentityProvider me={me.data}>{children}</IdentityProvider>;
}

function Splash() {
  return (
    <div className="flex min-h-svh items-center justify-center" aria-busy="true">
      <KorurLogo className="h-8 w-auto animate-pulse" />
    </div>
  );
}

function GatePage({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-svh flex-col items-center justify-center gap-8 px-4">
      <KorurLogo className="h-8 w-auto" />
      <Card className="w-full max-w-md gap-4 text-center">{children}</Card>
    </div>
  );
}

/**
 * Keeps the session: it is shared by every Oba app, and the user may still
 * belong to organizations in Kaizen. Signing in with another account must end
 * the browser's Keycloak session first, or the sign-in would silently reuse
 * this account; the sign-out lands on a sign-in that asks for credentials.
 * The account app is offered because the user answers invitations and
 * creates an organization there.
 */
function NotMember() {
  return (
    <GatePage>
      <CardHeader className="justify-items-center">
        <UserX className="text-muted-foreground size-8" aria-hidden />
        <CardTitle className="text-lg">You are not a member of an organization on Kaya</CardTitle>
        <CardDescription>
          Kaya opens the organizations you belong to on Oba. Ask an admin of your organization to
          invite you, or sign in with another account. Signing in with another account signs you
          out of every Oba app.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col items-center gap-2">
        <Button variant="outline" asChild>
          <a href={ACCOUNT_LINKS.profile}>
            <UserCog className="size-4" aria-hidden />
            Open your account
          </a>
        </Button>
        <Button onClick={submitLogout}>
          <LogIn className="size-4" aria-hidden />
          Sign in with another account
        </Button>
      </CardContent>
    </GatePage>
  );
}

/**
 * The user belongs to organizations, but none grants Kaya, or a call was just
 * refused because Kaya is no longer granted in the organization open. It keeps
 * the session and offers the same two ways out as NotMember, for the same
 * reasons: an owner or admin grants Kaya, and another account may hold it.
 * Reloading reads the grants again, so Kaya then opens an organization that
 * still grants it.
 */
function NoAppAccess() {
  return (
    <GatePage>
      <CardHeader className="justify-items-center">
        <ShieldOff className="text-muted-foreground size-8" aria-hidden />
        <CardTitle className="text-lg">Kaya is not open to you</CardTitle>
        <CardDescription>
          Kaya opens the organizations where an owner or admin granted it to you. Ask them for
          access, or sign in with another account. Signing in with another account signs you out of
          every Oba app.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col items-center gap-2">
        <Button variant="outline" asChild>
          <a href={ACCOUNT_LINKS.profile}>
            <UserCog className="size-4" aria-hidden />
            Open your account
          </a>
        </Button>
        <Button onClick={submitLogout}>
          <LogIn className="size-4" aria-hidden />
          Sign in with another account
        </Button>
      </CardContent>
    </GatePage>
  );
}

/**
 * The platform user is disabled, so every Oba app refuses this session. The
 * sign-out waits for a click rather than starting as the page opens: it leaves
 * for Keycloak, and the user would never read why they were signed out. It ends
 * the browser's Keycloak session too, so the next sign-in asks for credentials
 * instead of silently bringing the same account back here.
 */
function Disabled() {
  return (
    <GatePage>
      <CardHeader className="justify-items-center">
        <UserX className="text-muted-foreground size-8" aria-hidden />
        <CardTitle className="text-lg">Your account is disabled</CardTitle>
        <CardDescription>
          An admin of your organization can enable it again. Signing out signs you out of every Oba
          app.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Button onClick={submitLogout}>
          <LogOut className="size-4" aria-hidden />
          Sign out
        </Button>
      </CardContent>
    </GatePage>
  );
}

function SignInFailed({ reason }: { reason: string }) {
  return (
    <GatePage>
      <CardHeader className="justify-items-center">
        <AlertTriangle className="text-error size-8" aria-hidden />
        <CardTitle className="text-lg">Signing in failed</CardTitle>
        <CardDescription>
          Keycloak reported <code className="font-mono">{reason}</code>.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Button asChild>
          <a href={signInUrl("/")}>
            <LogIn className="size-4" aria-hidden />
            Try again
          </a>
        </Button>
      </CardContent>
    </GatePage>
  );
}

/**
 * Kaya's API refused a session that a fresh sign-in had just renewed. Signing
 * out first ends the browser's Keycloak session too, so the next sign-in asks
 * for the credentials again rather than reissuing tokens the API refuses.
 */
function SessionRejected() {
  return (
    <GatePage>
      <CardHeader className="justify-items-center">
        <AlertTriangle className="text-error size-8" aria-hidden />
        <CardTitle className="text-lg">Kaya did not accept your session</CardTitle>
        <CardDescription>
          You signed in, but Kaya&apos;s API refused the session. Sign out and sign in again; if
          this keeps happening, ask an admin to check the Kaya API.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Button onClick={submitLogout}>
          <LogIn className="size-4" aria-hidden />
          Sign out and sign in again
        </Button>
      </CardContent>
    </GatePage>
  );
}

function IdentityError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <GatePage>
      <CardHeader className="justify-items-center">
        <AlertTriangle className="text-error size-8" aria-hidden />
        <CardTitle className="text-lg">Kaya could not load your account</CardTitle>
        <CardDescription>{message}</CardDescription>
      </CardHeader>
      <CardContent>
        <Button onClick={onRetry}>Try again</Button>
      </CardContent>
    </GatePage>
  );
}
