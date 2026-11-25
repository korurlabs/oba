"use client";

import { useTranslations } from "next-intl";
import { Badge } from "@oba/ui/sky/badge";
import type { AgentStatus, RoutineFireStatus } from "../schemas";

/** The status of an agent's user. */
export function AgentStatusBadge({ status }: { status: AgentStatus }) {
  const t = useTranslations("agents");

  return (
    <Badge variant={status === "ACTIVE" ? "success" : status === "DISABLED" ? "error" : "warning"}>
      {t(`statuses.${status}`)}
    </Badge>
  );
}

const FIRE_STATUS_VARIANT = {
  FIRED: "success",
  FAILED: "error",
  RATE_LIMITED: "warning",
  UNAUTHORIZED: "error",
} as const satisfies Record<RoutineFireStatus, "success" | "error" | "warning">;

/** The vendor's answer to the last fire of an agent's routine. */
export function AgentFireStatusBadge({ status }: { status: RoutineFireStatus }) {
  const t = useTranslations("agents");

  return <Badge variant={FIRE_STATUS_VARIANT[status]}>{t(`fireStatuses.${status}`)}</Badge>;
}
