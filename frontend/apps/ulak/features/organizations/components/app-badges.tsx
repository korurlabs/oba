"use client";

import { useTranslations } from "next-intl";
import { Badge } from "@oba/ui/sky/badge";
import { GRANTED_APP_OF, type ObaAppId } from "@oba/shared/constants/apps";

/** Web app id of each grantable app value, the inverse of `GRANTED_APP_OF`. */
const APP_ID_OF_GRANTED_APP: Readonly<Record<string, ObaAppId>> = Object.fromEntries(
  Object.entries(GRANTED_APP_OF).map(([appId, grantedApp]) => [grantedApp, appId as ObaAppId])
);

/**
 * Display name of a wire app value. The API sends app values as plain
 * strings, so a value this build does not know yet is shown as it came
 * instead of failing the page.
 */
export function useAppName() {
  const t = useTranslations("apps");
  return (app: string): string => {
    const appId = APP_ID_OF_GRANTED_APP[app];
    return appId ? t(`${appId}.name`) : app;
  };
}

/** One badge per app, or `emptyLabel` in muted text when the list is empty. */
export function AppBadges({ apps, emptyLabel }: { apps: readonly string[]; emptyLabel: string }) {
  const appName = useAppName();

  if (apps.length === 0) {
    return <span className="text-muted-foreground text-sm">{emptyLabel}</span>;
  }

  return (
    <div className="flex flex-wrap gap-1">
      {apps.map((app) => (
        <Badge key={app} variant="secondary">
          {appName(app)}
        </Badge>
      ))}
    </div>
  );
}
