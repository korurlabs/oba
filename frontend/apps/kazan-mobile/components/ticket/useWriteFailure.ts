import { useCallback } from "react";

import { apiErrorToastMessage, isRuleRefusal } from "@/components/auth/apiErrorToastModel";
import { toast } from "@/components/ui/toast";
import { useTranslation } from "@/i18n";
import { toApiError, type ApiError } from "@/lib/api";

/**
 * Shows a failed ticket write as an error toast with Retry. The writes of the
 * ticket screen are sent with `_silentErrors`, so this toast replaces the
 * app-wide one rather than doubling it; the text is the same the app-wide
 * layer would pick (the server's message for a business rule, else the kind
 * of failure). A validation error is the caller's to show under its field.
 * A rule refusal gets no Retry, because the same write is refused again.
 */
export function useWriteFailure() {
  const { t } = useTranslation();
  return useCallback(
    (error: unknown, method: "post" | "put" | "delete", retry?: () => void): ApiError => {
      const apiError: ApiError = toApiError(error);
      const message: string =
        apiErrorToastMessage({ error: apiError, method, url: undefined }, t) ??
        t("apiErrorGeneric");
      toast.error(
        message,
        retry && !isRuleRefusal(apiError)
          ? { action: { label: t("ticketRetry"), onPress: retry } }
          : undefined
      );
      return apiError;
    },
    [t]
  );
}
