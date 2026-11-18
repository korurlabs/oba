"use client";

import { AlertCircle, RotateCw } from "lucide-react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

interface HomeSectionErrorProps {
  message: string;
  isRetrying: boolean;
  onRetry: () => void;
  className?: string;
}

/**
 * Takes the place of one home section whose fetch failed. Every section loads
 * on its own, so one failure leaves the rest of the dashboard usable and the
 * user retries only what is missing.
 */
export function HomeSectionError({ message, isRetrying, onRetry, className }: HomeSectionErrorProps) {
  const t = useTranslations("home");

  return (
    <div
      role="alert"
      className={cn(
        "border-destructive/30 bg-destructive/10 text-card-foreground flex items-center gap-4 rounded-2xl border p-4 sm:p-5",
        className,
      )}
    >
      <AlertCircle aria-hidden className="text-destructive size-5 shrink-0" />
      <p className="text-muted-foreground flex-1 text-sm">{message}</p>
      <Button
        type="button"
        variant="outline"
        size="sm"
        className="gap-2"
        disabled={isRetrying}
        onClick={onRetry}
      >
        <RotateCw aria-hidden className={cn("size-4", isRetrying && "animate-spin")} />
        {t("retry")}
      </Button>
    </div>
  );
}
