import type { Metadata } from "next";

import { AccountDetail } from "@/features/accounts/components";

export const metadata: Metadata = {
  title: "Account",
};

/** One account: its subscription, users, usage and machines. */
export default async function AccountRoute({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <AccountDetail accountId={id} />;
}
