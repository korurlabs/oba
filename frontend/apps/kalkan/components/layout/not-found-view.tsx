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
 * The screen of a `not-found.tsx` route file: the page does not exist, and a
 * way back to the start page at `/`.
 */
export function NotFoundView({ className }: NotFoundViewProps) {
  const t = useTranslations("errors");

  return (
    <div className={cn("bg-background flex items-center justify-center p-4", className)}>
      <div className="w-full max-w-md space-y-6 text-center">
        <div className="flex justify-center">
          <div className="bg-primary/10 rounded-full p-4">
            <FileQuestion className="text-primary h-12 w-12" aria-hidden="true" />
          </div>
        </div>

        <div className="space-y-2">
          <h1 className="text-foreground text-2xl font-bold tracking-tight">
            {t("notFound.title")}
          </h1>
          <p className="text-muted-foreground text-sm">{t("notFound.description")}</p>
        </div>

        <Button asChild className="gap-2">
          <Link href="/">
            <Home className="h-4 w-4" aria-hidden="true" />
            {t("goHome")}
          </Link>
        </Button>
      </div>
    </div>
  );
}
