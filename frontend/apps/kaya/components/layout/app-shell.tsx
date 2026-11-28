"use client";

import Image from "next/image";
import type { ReactNode } from "react";

import { AppSwitcher } from "@oba/shared/components/app-header/app-switcher";
import { OrganizationSelector } from "@oba/shared/components/app-header/organization-selector";
import { ProfileDropdown } from "@oba/shared/components/app-header/profile-dropdown";
import { ACCOUNT_LINKS } from "@oba/shared/constants/apps";
import { SkyAppShell } from "@oba/ui/sky/app-shell";

import { ChangeControl } from "@/features/versions/components/change-control";
import { ChangeModeFrame, OpenChangeProvider } from "@/features/versions/components/open-change";
import { useIdentity } from "@/lib/identity/identity-context";
import type { MembershipResponse } from "@/lib/identity/schemas";

import { guardNavigation, KAYA_NAV_SECTIONS, KAYA_REPOSITORIES_ITEM } from "./kaya-sidebar";
import { KayaLogo } from "./kaya-logo";

export type AppShellProps = {
  children: ReactNode;
};

/**
 * The organization selector of the top bar. Kaya holds the current
 * organization itself, so the selector is controlled: it offers the
 * memberships that open Kaya, saves the choice as the default organization so
 * the other apps follow, and then switches Kaya to it.
 */
function KayaOrganizationSelector() {
  const { me, organization, switchOrganization } = useIdentity();
  const organizations = me.memberships.map((membership: MembershipResponse) => ({
    id: membership.organizationId,
    name: membership.name,
  }));

  return (
    <OrganizationSelector
      controlled={{
        organizationId: organization.organizationId,
        organizations,
        onSwitch: switchOrganization,
      }}
    />
  );
}

/**
 * Page frame: the shared sky shell with Kaya's menu, its wordmark, the
 * member's change, the organization selector, the app switcher and the
 * account menu in the top bar, and Repositories at the foot of the menu. The
 * member's open change is known to every part of it, and change mode frames
 * the whole window. A menu link asks first while a config edit is unsaved.
 */
export function AppShell({ children }: AppShellProps) {
  // Profile is Account's page; Kaya has none of its own. Read on render,
  // because the account origin arrives with the run-time config.
  const profileHref = ACCOUNT_LINKS.profile;

  return (
    <OpenChangeProvider>
      <SkyAppShell
        logo={<KayaLogo className="h-6 w-auto sm:h-7" />}
        homeLabel="Kaya home"
        actions={
          <>
            <ChangeControl />
            <KayaOrganizationSelector />
            <AppSwitcher currentAppId="kaya" />
            <ProfileDropdown profileHref={profileHref} />
          </>
        }
        sections={KAYA_NAV_SECTIONS}
        footerItems={[KAYA_REPOSITORIES_ITEM]}
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
        onNavClick={(event) => guardNavigation(event)}
        overlay={<ChangeModeFrame />}
      >
        {children}
      </SkyAppShell>
    </OpenChangeProvider>
  );
}
