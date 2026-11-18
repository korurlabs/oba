"use client";

import { MachineUsage } from "@/features/machines/components";
import { cn } from "@/lib/utils";

import { providerReportsUsage, type AccountListResponse } from "../schemas";
import { NOT_REPORTED_FOR_PROVIDER } from "./account-text";

export type AccountUsageProps = {
  account: Pick<AccountListResponse, "provider" | "usage">;
  /** The current time, renewed by the page every 30 seconds. */
  now: number;
  className?: string;
};

/**
 * The 5-hour and 7-day windows of the account's newest snapshot. Those
 * windows exist only for providers the recorder reads, so any other provider
 * says so instead of showing an empty usage.
 */
export function AccountUsage({ account, now, className }: AccountUsageProps) {
  if (!providerReportsUsage(account.provider)) {
    return (
      <span className={cn("text-muted-foreground text-xs", className)}>
        {NOT_REPORTED_FOR_PROVIDER}
      </span>
    );
  }
  return <MachineUsage usage={account.usage} now={now} className={className} />;
}
