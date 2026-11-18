import type { Metadata } from "next";
import { Suspense } from "react";

import { AccountsPage } from "@/features/accounts/components";

export const metadata: Metadata = {
  title: "Accounts",
};

/** The organization's accounts with their usage; `?userId=` lives in the URL. */
export default function AccountsRoute() {
  return (
    <Suspense>
      <AccountsPage />
    </Suspense>
  );
}
