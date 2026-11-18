"use client";

import { Badge } from "@/components/ui/badge";
import { Crown, Shield, Edit, Eye } from "lucide-react";
import { useTranslations } from "next-intl";
import type { BoardMemberRole } from "../schemas";

const roleIcons: Record<BoardMemberRole, typeof Crown> = {
  OWNER: Crown,
  ADMIN: Shield,
  MEMBER: Edit,
  VIEWER: Eye,
};

const roleColors: Record<BoardMemberRole, string> = {
  OWNER: "bg-korur-blue-light text-korur-blue-strong dark:bg-korur-slate dark:text-korur-blue",
  ADMIN: "bg-korur-blue-accent text-korur-blue-deep dark:bg-korur-slate dark:text-korur-blue-medium",
  MEMBER: "bg-blue-100 dark:bg-blue-900 text-blue-800 dark:text-blue-200",
  VIEWER: "bg-gray-100 dark:bg-gray-900 text-gray-800 dark:text-gray-200",
};

interface BoardMemberRoleChipProps {
  role: BoardMemberRole;
  size?: "sm" | "default";
}

export function BoardMemberRoleChip({ role, size = "default" }: BoardMemberRoleChipProps) {
  const t = useTranslations("board.members.roles");
  const Icon = roleIcons[role];
  const color = roleColors[role];

  if (size === "sm") {
    return (
      <Badge className={`${color} px-1.5 py-0 text-[10px] leading-4`}>
        <Icon className="mr-0.5 h-2.5 w-2.5" />
        {t(role)}
      </Badge>
    );
  }

  return (
    <Badge className={color}>
      <Icon className="mr-1 h-3 w-3" />
      {t(role)}
    </Badge>
  );
}
