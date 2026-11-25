"use client";

import type { ReactNode } from "react";
import { ShieldOff } from "lucide-react";
import { useTranslations } from "next-intl";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import { EmptyState } from "@/components/ui/empty-state";
import { isCyberscoreClosed } from "../lib/cyberscore-access";

interface CyberscoreAccessGateProps {
  children: ReactNode;
}

/**
 * Shows a no-access state in place of a cyberscore page for a guest of the
 * selected organization, whom the server refuses every cyberscore read. The
 * page's queries live in its children, so for a guest they never mount and
 * no refused request goes out. The dashboard layout hydrates the user, so the
 * answer is known on the first render; while it is not known the page
 * renders and the API decides.
 */
export function CyberscoreAccessGate({ children }: CyberscoreAccessGateProps) {
  const t = useTranslations("cyberscore.noAccess");
  const { data: meResponse } = useGetMe();
  const me = meResponse?.data;
  const organizationId = me?.defaultOrganizationId;

  if (!isCyberscoreClosed(me, organizationId)) return children;

  const organizationName =
    me?.organizations?.find((organization) => organization.id === organizationId)?.name ?? "";
  return (
    <EmptyState
      Icon={ShieldOff}
      title={t("title")}
      description={t("description", { organization: organizationName })}
    />
  );
}
