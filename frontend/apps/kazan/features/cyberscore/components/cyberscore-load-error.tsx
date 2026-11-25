"use client";

import { AlertCircle, RotateCw } from "lucide-react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { inlineAlertVariants } from "@/components/ui/inline-alert";
import { cn } from "@/lib/utils";

interface CyberscoreLoadErrorProps {
  message: string;
  isRetrying: boolean;
  onRetry: () => void;
  className?: string;
}

/**
 * Takes the place of a cyberscore section whose fetch failed: Kazan's danger
 * notice (a card plate with a red leading rule, no tinted fill) with a retry,
 * so the rest of the page stays usable and only the missing part reloads.
 */
export function CyberscoreLoadError({
  message,
  isRetrying,
  onRetry,
  className,
}: CyberscoreLoadErrorProps) {
  const tErrors = useTranslations("errors");

  return (
    <div
      role="alert"
      className={cn(
        inlineAlertVariants({ variant: "danger" }),
        "gap-3 p-4 [&_button_svg]:text-current",
        className
      )}
    >
      <AlertCircle aria-hidden className="size-5 shrink-0" />
      <p className="text-foreground flex-1 text-sm">{message}</p>
      <Button
        type="button"
        variant="outline"
        size="sm"
        className="gap-2"
        disabled={isRetrying}
        onClick={onRetry}
      >
        <RotateCw aria-hidden className={cn("size-4", isRetrying && "animate-spin")} />
        {tErrors("tryAgain")}
      </Button>
    </div>
  );
}
