"use client";

import { Crown, Eye, Shield, Users, type LucideIcon } from "lucide-react";
import { useTranslations } from "next-intl";
import { Badge } from "@oba/ui/components/badge";
import type { OrganizationRole } from "@oba/shared/features/users/schema";

/** Icon of each organization role, shared by the role badge and the role pickers. */
export const ORGANIZATION_ROLE_ICONS: Record<OrganizationRole, LucideIcon> = {
  OWNER: Crown,
  ADMIN: Shield,
  MEMBER: Users,
  GUEST: Eye,
};

/** A user's role in an organization: the role icon and its translated name. */
export function OrganizationRoleBadge({ role }: { role: OrganizationRole }) {
  const t = useTranslations("organizationRoles");
  const Icon = ORGANIZATION_ROLE_ICONS[role];

  return (
    <Badge variant="secondary" className="gap-1.5 whitespace-nowrap">
      <Icon className="h-3 w-3" />
      {t(role)}
    </Badge>
  );
}
