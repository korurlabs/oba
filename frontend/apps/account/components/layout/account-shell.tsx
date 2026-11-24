"use client";

import Image from "next/image";
import { useTranslations } from "next-intl";
import { Mail, Settings, User } from "lucide-react";
import type { ReactNode } from "react";
import { AppSwitcher } from "@oba/shared/components/app-header/app-switcher";
import { ProfileDropdown } from "@oba/shared/components/app-header/profile-dropdown";
import { LanguageSwitcherSubMenu } from "@oba/shared/components/language-switcher";
import { Link, usePathname } from "@oba/shared/libs/i18n/navigation";
import { SkyAppShell, type SkyNavSection } from "@oba/ui/sky/app-shell";
import { SUPPORTED_LOCALES } from "@/lib/i18n/supported-locales";
import { AccountSessionSync } from "./account-session-sync";

/**
 * The profile lives at `/`, the root of this app, so the user menu cannot use
 * the `/profile` default of `ProfileDropdown`.
 */
const ACCOUNT_HREFS = {
  profile: "/",
  settings: "/settings",
};

/**
 * Page frame of every signed-in Account page, shared by the gateway profile
 * page and the dashboard layout: the shared sky shell with the profile and the
 * invitations in the menu, Settings at its foot, and the app switcher and the
 * account menu in the top bar. The links and the active page go through the
 * locale-aware navigation, so a locale prefix neither breaks a link nor hides
 * the page in view.
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
        { label: t("invitations"), href: "/invitations", icon: Mail },
      ],
    },
  ];

  return (
    <SkyAppShell
      logo={
        <span className="flex items-center gap-2">
          <Image
            src="/assets/korur-mark.png"
            alt=""
            width={24}
            height={24}
            className="rounded bg-white"
          />
          <span className="text-sm font-semibold">{tApps("account.name")}</span>
        </span>
      }
      homeLabel={tApps("account.name")}
      actions={
        <>
          <AppSwitcher currentAppId="account" />
          <ProfileDropdown
            hrefs={ACCOUNT_HREFS}
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
