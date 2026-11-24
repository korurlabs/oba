"use client";

import type { LucideIcon } from "lucide-react";
import { Columns3, Download, Siren } from "lucide-react";
import { useTranslations } from "next-intl";
import { Link, usePathname } from "@oba/shared/libs/i18n/navigation";
import { cn } from "@oba/ui/lib/utils";

type AccountTabId = "tableColumns" | "alerts" | "downloads";

interface AccountTab {
  id: AccountTabId;
  href: string;
  icon: LucideIcon;
}

/**
 * Kazan's own settings screens. `id` doubles as the label key in the
 * `sidebar` namespace. Download CLI comes last: the downloads page sits
 * outside the settings screens, so its tab is never active. The profile,
 * invitations and general settings live in the account app and the
 * organizations in Ulak; the profile menu and the app switcher reach them, so
 * this row does not repeat them.
 */
const ACCOUNT_TABS: readonly AccountTab[] = [
  { id: "tableColumns", href: "/settings/columns", icon: Columns3 },
  { id: "alerts", href: "/settings/alerts", icon: Siren },
  { id: "downloads", href: "/downloads", icon: Download },
];

/**
 * Navigation row shared by the Kazan settings screens (table columns,
 * alerts), with the CLI downloads beside them. Rendered by the `(account)`
 * layout, so it stays on screen when one of those pages throws.
 *
 * Modelled on `board-tabs.tsx`: a row of links whose active tab comes from the
 * pathname. The row scrolls horizontally when the tabs do not fit the width.
 */
export function AccountNav() {
  const t = useTranslations("sidebar");
  const pathname = usePathname();

  const isTabActive = (tab: AccountTab): boolean => tab.id !== "downloads" && pathname === tab.href;

  return (
    <div className="border-border bg-card shrink-0 border-b">
      <nav className="flex gap-6 overflow-x-auto px-6" aria-label={t("settings")}>
        {ACCOUNT_TABS.map((tab) => {
          const isActive = isTabActive(tab);
          const Icon = tab.icon;

          return (
            <Link
              key={tab.id}
              href={tab.href}
              className={cn(
                "relative flex shrink-0 items-center gap-2 py-3 text-sm font-medium whitespace-nowrap transition-colors",
                "focus-visible:ring-primary focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:outline-none",
                isActive ? "text-foreground" : "text-muted-foreground hover:text-foreground"
              )}
              aria-current={isActive ? "page" : undefined}
            >
              <Icon className="h-4 w-4" aria-hidden="true" />
              {t(tab.id)}

              {/* Active indicator */}
              {isActive && (
                <span
                  className="bg-primary absolute right-0 bottom-0 left-0 h-0.5"
                  aria-hidden="true"
                />
              )}
            </Link>
          );
        })}
      </nav>
    </div>
  );
}
