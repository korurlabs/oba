"use client";

import Image from "next/image";
import type { ReactNode } from "react";

import { SkyAppShell } from "@oba/ui/sky/app-shell";

import { ChangeControl } from "@/features/versions/components/change-control";
import { ChangeModeFrame, OpenChangeProvider } from "@/features/versions/components/open-change";

import { guardNavigation, KAYA_NAV_SECTIONS, KAYA_SETTINGS_ITEM } from "./kaya-sidebar";
import { KayaLogo } from "./kaya-logo";
import { UserMenu } from "./user-menu";

export type AppShellProps = {
  children: ReactNode;
};

/**
 * Page frame: the shared sky shell with Kaya's menu, its wordmark, the
 * member's change and the account menu in the top bar, and Settings at the
 * foot of the menu. The member's open change is known to every part of it,
 * and change mode frames the whole window. A menu link asks first while a
 * config edit is unsaved.
 */
export function AppShell({ children }: AppShellProps) {
  return (
    <OpenChangeProvider>
      <SkyAppShell
        logo={<KayaLogo className="h-6 w-auto sm:h-7" />}
        homeLabel="Kaya home"
        actions={
          <>
            <ChangeControl />
            <UserMenu />
          </>
        }
        sections={KAYA_NAV_SECTIONS}
        footerItems={[KAYA_SETTINGS_ITEM]}
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
