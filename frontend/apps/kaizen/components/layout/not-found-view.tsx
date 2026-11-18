import { FileQuestion, Home } from "lucide-react";
import { useTranslations } from "next-intl";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

interface NotFoundViewProps {
  /** Height of the screen: `min-h-screen` outside the shell, `flex-1` inside it. */
  className: string;
}

/**
 * The screen of a `not-found.tsx` route file: the page does not exist or the
 * user may not see it, and a way back to Home. It says the same for both
 * cases, so it never reveals whether a board or ticket the user cannot
 * access exists.
 */
export function NotFoundView({ className }: NotFoundViewProps) {
  const t = useTranslations("errors");

  return (
    <div className={cn("flex items-center justify-center bg-background p-4", className)}>
      <div className="w-full max-w-md space-y-6 text-center">
        <div className="flex justify-center">
          <div className="rounded-full bg-primary/10 p-4">
            <FileQuestion className="h-12 w-12 text-primary" aria-hidden="true" />
          </div>
        </div>

        <div className="space-y-2">
          <h1 className="text-2xl font-bold tracking-tight text-foreground">
            {t("notFound.title")}
          </h1>
          <p className="text-sm text-muted-foreground">{t("notFound.description")}</p>
        </div>

        <Button asChild className="gap-2">
          <Link href="/home">
            <Home className="h-4 w-4" aria-hidden="true" />
            {t("goHome")}
          </Link>
        </Button>
      </div>
    </div>
  );
}
