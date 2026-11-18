"use client";

import { useEffect } from "react";
import { AlertTriangle, RefreshCcw, Home } from "lucide-react";
import { devError } from "@oba/shared/helpers/dev-log";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
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
      className={cn("bg-background flex items-center justify-center p-4", className)}
      role="alert"
      aria-live="assertive"
    >
      <div className="w-full max-w-md space-y-6 text-center">
        <div className="flex justify-center">
          <div className="bg-destructive/10 rounded-full p-4">
            <AlertTriangle className="text-destructive h-12 w-12" aria-hidden="true" />
          </div>
        </div>

        <div className="space-y-2">
          <h1 className="text-foreground text-2xl font-bold tracking-tight">
            {t("somethingWentWrong")}
          </h1>
          <p className="text-muted-foreground text-sm">{t("unexpectedError")}</p>
        </div>

        {process.env.NODE_ENV === "development" && (
          <div className="border-destructive/20 bg-destructive/5 rounded-lg border p-4 text-left">
            <p className="text-destructive mb-2 text-xs font-medium">{t("errorDetails")}</p>
            <pre className="text-muted-foreground overflow-auto text-xs">{error.message}</pre>
            {error.digest && (
              <p className="text-muted-foreground mt-2 text-xs">Digest: {error.digest}</p>
            )}
          </div>
        )}

        <div className="flex flex-col gap-2 sm:flex-row sm:justify-center">
          <Button onClick={reset} variant="default" className="gap-2" aria-label={t("tryAgain")}>
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
