"use client";

import Image from "next/image";
import { useTranslations } from "next-intl";
import { Bell, Building2, Mail, Settings, ShieldCheck, User } from "lucide-react";
import type { ReactNode } from "react";
import { AppSwitcher } from "@oba/shared/components/app-header/app-switcher";
import { OrganizationSelector } from "@oba/shared/components/app-header/organization-selector";
import { ProfileDropdown } from "@oba/shared/components/app-header/profile-dropdown";
import { LanguageSwitcherSubMenu } from "@oba/shared/components/language-switcher";
import { Link, usePathname } from "@oba/shared/libs/i18n/navigation";
import { SkyAppShell, type SkyNavSection } from "@oba/ui/sky/app-shell";
import { SUPPORTED_LOCALES } from "@/lib/i18n/supported-locales";
import { AccountLogo } from "./account-logo";
import { AccountSessionSync } from "./account-session-sync";

/**
 * The profile lives at `/`, the root of this app, so the user menu cannot use
 * the `/profile` default of `ProfileDropdown`.
 */
const PROFILE_HREF = "/";

/**
 * Page frame of every signed-in Account page, shared by the gateway profile
 * page and the dashboard layout: the shared sky shell with the profile, the
 * organizations, security, notifications and invitations in the menu, Settings
 * at its foot, and the organization selector (every organization of the user,
 * as Account is open in each), the app switcher and the account menu in the
 * top bar. The links and the active page go through the locale-aware
 * navigation, so a locale prefix neither breaks a link nor hides the page in
 * view.
 */
export function AccountShell({ children }: { children: ReactNode }) {
  const t = useTranslations("sidebar");
  const tApps = useTranslations("apps");
  const pathname = usePathname();

  const sections: readonly SkyNavSection[] = [
    {
      label: t("navigation"),
      items: [
        { label: t("profile"), href: "/", icon: User },
        { label: t("organizations"), href: "/organizations", icon: Building2 },
        { label: t("security"), href: "/security", icon: ShieldCheck },
        { label: t("notifications"), href: "/notifications", icon: Bell },
        { label: t("invitations"), href: "/invitations", icon: Mail },
      ],
    },
  ];

  return (
    <SkyAppShell
      logo={<AccountLogo className="h-6 w-auto sm:h-7" />}
      homeLabel={tApps("account.name")}
      actions={
        <>
          <OrganizationSelector />
          <AppSwitcher currentAppId="account" />
          <ProfileDropdown
            profileHref={PROFILE_HREF}
            extraMenuContent={
              <LanguageSwitcherSubMenu supportedLocales={SUPPORTED_LOCALES} label={t("language")} />
            }
          />
        </>
      }
      sections={sections}
      footerItems={[{ label: t("settings"), href: "/settings", icon: Settings }]}
      footerMark={
        <Image
          src="/assets/korur-mark.png"
          alt=""
          aria-hidden="true"
          width={192}
          height={192}
          className="sky-watermark pointer-events-none absolute top-1/2 right-4 size-9 -translate-y-1/2 object-contain group-data-[collapsible=icon]:hidden"
        />
      }
      pathname={pathname}
      linkComponent={Link}
      overlay={<AccountSessionSync />}
    >
      {children}
    </SkyAppShell>
  );
}
