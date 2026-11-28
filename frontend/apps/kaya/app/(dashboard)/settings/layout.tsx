import type { Metadata } from "next";
import type { ReactNode } from "react";

import { PageHeading } from "@oba/ui/sky/page-heading";

export const metadata: Metadata = {
  title: "Settings",
};

/** The settings pages share a heading. */
export default function SettingsLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex flex-col gap-6">
      <PageHeading title="Settings" />
      {children}
    </div>
  );
}
