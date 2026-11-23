"use client";

import { useTranslations } from "next-intl";
import { Badge } from "@oba/ui/sky/badge";
import { STATUS_OPTIONS } from "@oba/shared/features/organizations/lib/organization-options";
import type { OrganizationMemberStatus } from "@oba/shared/features/organizations/schemas";

/** A member's status in an organization: the status icon and its translated name. */
export function OrganizationMemberStatusBadge({
  status,
}: {
  status: OrganizationMemberStatus;
}) {
  const t = useTranslations("organization");
  const Icon = STATUS_OPTIONS.find((opt) => opt.value === status)?.icon;

  return (
    <Badge
      variant={status === "DISABLED" ? "error" : "success"}
      className="gap-1.5 whitespace-nowrap"
    >
      {Icon && <Icon className="h-3 w-3" />}
      {t(`members.statuses.${status}`)}
    </Badge>
  );
}
