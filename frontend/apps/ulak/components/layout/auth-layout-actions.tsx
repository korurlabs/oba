"use client";

import { Suspense } from "react";
import { useSession } from "next-auth/react";
import { LogoutButton } from "@oba/shared/components/logout-button";
import { LanguageSwitcher } from "@oba/shared/components/language-switcher";
import { AuthError } from "@oba/shared/libs/auth/types";
import { SUPPORTED_LOCALES } from "@/lib/i18n/supported-locales";

/**
 * Top-right controls of the auth layout: the language switcher, and the
 * logout button for a signed-in user whose session is still usable.
 */
export function AuthLayoutActions() {
  const { data: session, status } = useSession();

  return (
    <div className="absolute top-4 right-4 flex items-center gap-2">
      <Suspense>
        <LanguageSwitcher supportedLocales={SUPPORTED_LOCALES} />
      </Suspense>
      {status === "authenticated" && session?.error !== AuthError.REFRESH_TOKEN_ERROR && (
        <LogoutButton />
      )}
    </div>
  );
}
