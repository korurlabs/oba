/**
 * Account Layout
 *
 * Shared layout for the Kazan settings screens: table columns and alerts.
 * `(account)` is a route group, so it adds no URL
 * segment. It renders the account navigation row above the page, the same
 * arrangement the board layout uses for `BoardTabs`: the row stays pinned and
 * the page scrolls underneath it.
 */

import { AccountNav } from "@/components/layout/account-nav";

export default function AccountLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex h-full flex-col">
      <AccountNav />
      <div className="min-h-0 flex-1 overflow-auto">{children}</div>
    </div>
  );
}
