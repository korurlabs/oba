"use client";

import { LayoutGrid } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId } from "react";
import { Button } from "@oba/ui/components/button";
import { Popover, PopoverContent, PopoverTrigger } from "@oba/ui/components/popover";
import {
  OPS_GRID_ACCENT,
  OPS_GRID_CURRENT,
  OPS_GRID_HAIRLINE,
  OPS_GRID_HEADING,
  OPS_GRID_HIGHLIGHT,
  OPS_GRID_ROW,
  OPS_GRID_SHADOW,
  OPS_GRID_SURFACE,
} from "@oba/ui/components/ops-grid";
import { cn } from "@oba/ui/lib/utils";
import { obaApps, type ObaApp, type ObaAppId } from "../../constants/apps";
import { canOpenApp } from "../../features/organizations/lib/app-access";
import { useGetMe } from "../../features/users/hooks/use-get-me";
import { useRuntimeConfig } from "../../providers/RuntimeConfigProvider";

/**
 * Ulak manages the organization itself, so it leads the grid; the product apps
 * follow in their catalogue order.
 */
const LEADING_APP_IDS: readonly ObaAppId[] = ["ulak"];

/** The apps in switcher order: the leading ones first, then the rest in catalogue order. */
function switcherOrder(apps: readonly ObaApp[]): ObaApp[] {
  return [
    ...LEADING_APP_IDS.flatMap((id) => apps.filter((app) => app.id === id)),
    ...apps.filter((app) => !LEADING_APP_IDS.includes(app.id)),
  ];
}

/** The square grid panel of the header menus; wide enough for two columns of tiles. */
const PANEL = cn(OPS_GRID_SURFACE, OPS_GRID_SHADOW, "w-[min(22rem,calc(100vw-2rem))] px-2 py-1.5");
/** An app tile: a grid row laid out as a name over its description, which wraps. */
const TILE = cn(
  OPS_GRID_ROW,
  OPS_GRID_HAIRLINE,
  "relative flex h-full flex-col items-stretch gap-1"
);

export interface AppSwitcherProps {
  /**
   * The app rendering the switcher; its tile is marked as current and not
   * linked. Account has no tile, so inside Account no tile is current.
   */
  currentAppId: ObaAppId;
  /** Applied to the trigger. */
  className?: string;
}

/**
 * Grid of the Oba web apps behind an icon button, in the square grid panel
 * of the organization selector beside it. Every other app opens in
 * the same tab: they share the Keycloak session, so the user lands signed in.
 *
 * Account is not listed: the profile menu leads to the user's own pages.
 * An app is listed only when it has something to show the user: Ulak for a
 * user who manages an organization, Kazan, Kaya, Kalkan and Otak for a user
 * with an ACTIVE organization that grants them. The current app is always listed.
 *
 * While the user is unknown (loading, or the load failed) Ulak stays hidden,
 * so that a member never sees a link that leads nowhere. The granted apps stay
 * listed in that state, because most users can open them and the API refuses
 * them anyway when no grant exists; hiding them would make them flicker.
 */
export function AppSwitcher({ currentAppId, className }: AppSwitcherProps) {
  const t = useTranslations("apps");
  const { data: meResponse } = useGetMe();
  const me = meResponse?.data;
  const { appUrls } = useRuntimeConfig();
  const headingId = useId();
  const apps = switcherOrder(obaApps(appUrls)).filter((app) => {
    if (app.id === currentAppId) return true;
    if (!me && app.id !== "ulak") return true;
    return canOpenApp(me, app.id);
  });

  // Tiles fill two columns; hairlines part the rows and the columns, never the
  // outer edge, which the panel's own border draws. A last tile left alone
  // spans both columns, so the hairline above it runs the full width.
  const isOdd = apps.length % 2 === 1;
  const lastRowStart = apps.length - (isOdd ? 1 : 2);

  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button
          variant="ghost"
          size="icon"
          className={cn("data-[state=open]:bg-muted h-8 w-8 cursor-pointer", className)}
          aria-label={t("switchApp")}
          title={t("switchApp")}
        >
          <LayoutGrid className="h-4 w-4" />
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" sideOffset={8} className={PANEL}>
        <p id={headingId} className={OPS_GRID_HEADING}>
          {t("switchApp")}
        </p>
        <div role="group" aria-labelledby={headingId} className="grid grid-cols-2">
          {apps.map((app, index) => {
            const isCurrent = app.id === currentAppId;
            const Icon = app.icon;
            const tileClassName = cn(
              TILE,
              index % 2 === 0 && index !== apps.length - 1 && "border-e",
              index >= lastRowStart && "border-b-0",
              isOdd && index === apps.length - 1 && "col-span-2"
            );
            const tile = (
              <>
                <span className="flex items-center gap-2">
                  <Icon className={cn("size-4 shrink-0", OPS_GRID_ACCENT)} aria-hidden="true" />
                  <span>{t(`${app.id}.name`)}</span>
                </span>
                <span className="text-muted-foreground text-xs leading-snug font-normal">
                  {t(`${app.id}.description`)}
                </span>
              </>
            );

            return isCurrent ? (
              // Marked like the checked row of a menu, so the current app reads
              // the same as the current organization beside it.
              <div
                key={app.id}
                aria-current="page"
                data-state="checked"
                className={cn(tileClassName, OPS_GRID_CURRENT)}
              >
                {tile}
              </div>
            ) : (
              <a key={app.id} href={app.url} className={cn(tileClassName, OPS_GRID_HIGHLIGHT)}>
                {tile}
              </a>
            );
          })}
        </div>
      </PopoverContent>
    </Popover>
  );
}
