"use client";

import { LayoutGrid } from "lucide-react";
import { useTranslations } from "next-intl";
import { Button } from "@oba/ui/components/button";
import { Popover, PopoverContent, PopoverTrigger } from "@oba/ui/components/popover";
import { cn } from "@oba/ui/lib/utils";
import { obaApps, type ObaApp, type ObaAppId } from "../../constants/apps";
import { canOpenApp } from "../../features/organizations/lib/app-access";
import { useGetMe } from "../../features/users/hooks/use-get-me";
import { useRuntimeConfig } from "../../providers/RuntimeConfigProvider";

/**
 * Admin and Account manage the organization and the user themselves, so they
 * lead the grid; the product apps follow in their catalogue order.
 */
const LEADING_APP_IDS: readonly ObaAppId[] = ["admin", "account"];

/** The apps in switcher order: the leading ones first, then the rest in catalogue order. */
function switcherOrder(apps: readonly ObaApp[]): ObaApp[] {
  return [
    ...LEADING_APP_IDS.flatMap((id) => apps.filter((app) => app.id === id)),
    ...apps.filter((app) => !LEADING_APP_IDS.includes(app.id)),
  ];
}

export interface AppSwitcherProps {
  /** The app rendering the switcher; it is marked as current and not linked. */
  currentAppId: ObaAppId;
  /** Applied to the trigger. */
  className?: string;
}

/**
 * Grid of the Oba web apps behind an icon button. Every other app opens in
 * the same tab: they share the Keycloak session, so the user lands signed in.
 *
 * An app is listed only when it has something to show the user: Admin for a
 * user who manages an organization, Kazan, Kaya, Kalkan and Otak for a user
 * with an ACTIVE organization that grants them. The current app is always listed.
 *
 * While the user is unknown (loading, or the load failed) Admin stays hidden,
 * so that a member never sees a link that leads nowhere. The granted apps stay
 * listed in that state, because most users can open them and the API refuses
 * them anyway when no grant exists; hiding them would make them flicker.
 */
export function AppSwitcher({ currentAppId, className }: AppSwitcherProps) {
  const t = useTranslations("apps");
  const { data: meResponse } = useGetMe();
  const me = meResponse?.data;
  const { appUrls } = useRuntimeConfig();
  const apps = switcherOrder(obaApps(appUrls)).filter((app) => {
    if (app.id === currentAppId) return true;
    if (!me && app.id !== "admin") return true;
    return canOpenApp(me, app.id);
  });

  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button
          variant="ghost"
          size="icon"
          className={cn("h-8 w-8 cursor-pointer", className)}
          aria-label={t("switchApp")}
          title={t("switchApp")}
        >
          <LayoutGrid className="h-4 w-4" />
        </Button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-[340px] p-3">
        <p className="text-muted-foreground mb-2 px-1 text-xs font-medium">{t("switchApp")}</p>
        <div className="grid grid-cols-2 gap-2">
          {apps.map((app) => {
            const isCurrent = app.id === currentAppId;
            const Icon = app.icon;
            const card = (
              <>
                <span className="flex items-center gap-2">
                  <Icon className="h-4 w-4 shrink-0" aria-hidden="true" />
                  <span className="text-sm font-medium">{t(`${app.id}.name`)}</span>
                </span>
                <span className="text-muted-foreground text-xs leading-tight">
                  {t(`${app.id}.description`)}
                </span>
              </>
            );
            const cardClassName = "flex h-full flex-col gap-1 rounded-lg border p-3 transition-colors";

            return isCurrent ? (
              <div
                key={app.id}
                aria-current="page"
                className={cn(cardClassName, "border-primary bg-primary/5")}
              >
                {card}
              </div>
            ) : (
              <a key={app.id} href={app.url} className={cn(cardClassName, "hover:bg-accent")}>
                {card}
              </a>
            );
          })}
        </div>
      </PopoverContent>
    </Popover>
  );
}
