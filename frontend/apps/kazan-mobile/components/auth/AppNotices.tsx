import { useEffect } from "react";

import { apiErrorToastMessage } from "@/components/auth/apiErrorToastModel";
import { toast } from "@/components/ui/toast";
import { useTranslation } from "@/i18n";
import { addApiErrorListener } from "@/lib/api";

/**
 * Turns a failed write anywhere in the app into an error toast
 * (`apiErrorToastMessage` says which failures belong to a form or a screen
 * instead). Without this listener a failed request would only be logged.
 * Renders nothing; mount it once next to ToastProvider.
 *
 * A session that Keycloak ended gets no toast: Sign in shows that notice
 * inline, where the user has to act on it.
 */
export function AppNotices() {
  const { t } = useTranslation();

  useEffect(
    () =>
      addApiErrorListener((event) => {
        const message: string | null = apiErrorToastMessage(event, t);
        if (message !== null) toast.error(message);
      }),
    [t]
  );

  return null;
}
