"use client";

import { useEffect } from "react";
import { AlertTriangle, RefreshCcw, Home } from "lucide-react";
import { devError } from "@oba/shared/helpers/dev-log";
import { useTranslations } from "next-intl";
import { Button } from "@oba/ui/sky/button";
import { cn } from "@/lib/utils";

interface RouteErrorProps {
  error: Error & { digest?: string };
  reset: () => void;
  /** Height of the screen: `min-h-screen` outside the shell, `min-h-full` inside it. */
  className: string;
}

/**
 * The error screen of a route error boundary: what went wrong, a retry and a
 * way home. Rendered by the `error.tsx` route files.
 */
export function RouteError({ error, reset, className }: RouteErrorProps) {
  const t = useTranslations("errors");

  useEffect(() => {
    devError("Page error:", error);
  }, [error]);

  return (
    <div
      className={cn("flex items-center justify-center p-4", className)}
      role="alert"
      aria-live="assertive"
    >
      <div className="w-full max-w-md space-y-6 text-center">
        <div className="flex justify-center">
          <div className="border-error/40 bg-error-light rounded-xl border p-4">
            <AlertTriangle className="h-12 w-12 text-destructive" aria-hidden="true" />
          </div>
        </div>

        <div className="space-y-2">
          <h1 className="text-2xl">
            {t("somethingWentWrong")}
          </h1>
          <p className="text-sm text-muted-foreground">{t("unexpectedError")}</p>
        </div>

        {process.env.NODE_ENV === "development" && (
          <div className="rounded-xl border border-destructive/20 bg-destructive/5 p-4 text-left">
            <p className="mb-2 text-xs font-medium text-destructive">{t("errorDetails")}</p>
            <pre className="overflow-auto text-xs text-muted-foreground">{error.message}</pre>
            {error.digest && (
              <p className="mt-2 text-xs text-muted-foreground">Digest: {error.digest}</p>
            )}
          </div>
        )}

        <div className="flex flex-col gap-2 sm:flex-row sm:justify-center">
          <Button
            onClick={reset}
            variant="default"
            className="gap-2"
            aria-label={t("tryAgain")}
          >
            <RefreshCcw className="h-4 w-4" aria-hidden="true" />
            {t("tryAgain")}
          </Button>
          <Button
            onClick={() => (window.location.href = "/")}
            variant="outline"
            className="gap-2"
            aria-label={t("goHome")}
          >
            <Home className="h-4 w-4" aria-hidden="true" />
            {t("goHome")}
          </Button>
        </div>
      </div>
    </div>
  );
}
