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

/*
 * Outline chips in the grid inks: the roles that manage the board (owner,
 * admin) carry the primary ink, the others plain ink; the icon and the word
 * tell the role apart.
 */
const roleColors: Record<BoardMemberRole, string> = {
  OWNER: "bg-transparent border-primary/50 text-korur-blue-strong hover:bg-transparent dark:text-korur-blue",
  ADMIN: "bg-transparent border-primary/30 text-korur-blue-strong hover:bg-transparent dark:text-korur-blue-medium",
  MEMBER: "bg-transparent border-border text-foreground hover:bg-transparent",
  VIEWER: "bg-transparent border-border text-muted-foreground hover:bg-transparent",
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
