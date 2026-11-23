"use client";

import type { LucideIcon } from "lucide-react";
import { Building2, Columns3, Mail, Settings, Siren, User } from "lucide-react";
import { ACCOUNT_LINKS, ULAK_LINKS } from "@oba/shared/constants/apps";
import { canManageOrganizations } from "@oba/shared/features/organizations/lib/managed-organizations";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import { useTranslations } from "next-intl";
import { Link, usePathname } from "@oba/shared/libs/i18n/navigation";
import { cn } from "@oba/ui/lib/utils";

type AccountTabId =
  | "profile"
  | "invitations"
  | "general"
  | "tableColumns"
  | "organizations"
  | "alerts";

interface AccountTab {
  id: AccountTabId;
  /** An in-app path, or an absolute URL for a screen that lives in another Oba app. */
  href: string;
  icon: LucideIcon;
}

/**
 * The account screens. `id` doubles as the label key in the `sidebar`
 * namespace. Profile, my invitations and general settings are screens of the
 * account app, and organizations a Ulak screen, so their tabs leave Kazan
 * and are never active here. Built on render, because the other apps' origins
 * are known only once the run-time config has arrived.
 */
const accountTabs = (): AccountTab[] => [
  { id: "profile", href: ACCOUNT_LINKS.profile, icon: User },
  { id: "invitations", href: ACCOUNT_LINKS.invitations, icon: Mail },
  { id: "general", href: ACCOUNT_LINKS.settings, icon: Settings },
  { id: "tableColumns", href: "/settings/columns", icon: Columns3 },
  { id: "organizations", href: ULAK_LINKS.organizations, icon: Building2 },
  { id: "alerts", href: "/settings/alerts", icon: Siren },
];

/**
 * Navigation row shared by the Kazan settings screens (table columns,
 * alerts), with the account app screens (profile, my invitations, general
 * settings) and the Ulak organizations screen beside them. Rendered by the
 * `(account)` layout, so it stays on screen when one of those pages throws.
 * The organizations tab shows only for a user who manages an organization,
 * since Ulak refuses everyone else; while the user is unknown it stays hidden.
 *
 * Modelled on `board-tabs.tsx`: a row of links whose active tab comes from the
 * pathname. The row scrolls horizontally when the tabs do not fit the width.
 */
export function AccountNav() {
  const t = useTranslations("sidebar");
  const pathname = usePathname();
  const { data: meResponse } = useGetMe();
  const managesOrganizations = canManageOrganizations(meResponse?.data);
  const tabs = accountTabs().filter((tab) => tab.id !== "organizations" || managesOrganizations);

  const isTabActive = (tab: AccountTab): boolean => pathname === tab.href;

  return (
    <div className="border-border bg-card shrink-0 border-b">
      <nav className="flex gap-6 overflow-x-auto px-6" aria-label={t("settings")}>
        {tabs.map((tab) => {
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
