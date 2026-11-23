import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { redirect as nextRedirect } from "next/navigation";
import { Plus, ShieldX, UserCog } from "lucide-react";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { auth } from "@oba/shared/libs/auth";
import { AuthError } from "@oba/shared/libs/auth/types";
import { redirect, redirectToAuthApp } from "@oba/shared/libs/i18n/navigation";
import { Locale } from "@oba/shared/types";
import { prefetchMe } from "@oba/shared/features/users/api/prefetch-me";
import { USERS_KEYS, type usersApi } from "@oba/shared/features/users/hooks/use-get-me";
import { canManageOrganizations } from "@oba/shared/features/organizations/lib/managed-organizations";
import { accountOnboardingUrl, ACCOUNT_LINKS, appUrl } from "@oba/shared/constants/apps";
import { Button } from "@oba/ui/sky/button";

export const dynamic = "force-dynamic";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("noAccess");
  return {
    title: t("title"),
    description: t("description"),
  };
}

/**
 * No Access Page
 *
 * Where Ulak sends a signed-in user who manages no active organization. The
 * user's own pages, invitations and organization creation live in the account
 * app, so the page links there. Signing out is the auth layout's button.
 *
 * The page stays private: a visitor without a session signs in first. A
 * manager who opens it, or who gained a role since, goes to the list. When
 * the user cannot be loaded, the page renders: the list would send a
 * non-manager back here, and its own gate handles a manager.
 */
export default async function NoAccessPage({ params }: { params: Promise<{ locale: string }> }) {
  const locale = (await params).locale as Locale;
  const session = await auth();

  if (!session?.user || session.error === AuthError.REFRESH_TOKEN_ERROR) {
    return redirectToAuthApp(locale, "login");
  }

  // Pending users onboard in the account app, which returns them to the list.
  if (session.userStatus === "PENDING_ONBOARDING") {
    return nextRedirect(accountOnboardingUrl("/", appUrl("ulak")));
  }

  const queryClient = makeQueryClient();
  await prefetchMe(queryClient, session.accessToken, "no-access-page");
  const me = queryClient.getQueryData<Awaited<ReturnType<typeof usersApi.getMe>>>(USERS_KEYS.me);
  if (me?.data && canManageOrganizations(me.data)) {
    redirect({ href: "/", locale });
  }

  const t = await getTranslations("noAccess");

  return (
    <div className="bg-card flex w-full max-w-md flex-col items-center gap-4 rounded-xl border p-6 text-center shadow-sm">
      <ShieldX className="text-muted-foreground size-8" aria-hidden />
      <h1 className="text-xl">{t("title")}</h1>
      <p className="text-muted-foreground text-sm">{t("description")}</p>
      {/* The account app is another origin, hence plain links. */}
      <div className="flex flex-col items-center gap-2">
        <Button variant="outline" asChild>
          <a href={ACCOUNT_LINKS.profile}>
            <UserCog className="size-4" aria-hidden />
            {t("openAccount")}
          </a>
        </Button>
        <Button asChild>
          <a href={ACCOUNT_LINKS.newOrganization}>
            <Plus className="size-4" aria-hidden />
            {t("createOrganization")}
          </a>
        </Button>
      </div>
    </div>
  );
}
