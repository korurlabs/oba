"use client";

import { Palette } from "lucide-react";
import { useTranslations } from "next-intl";
import { DropdownMenuItem } from "@oba/ui/components/dropdown-menu";
import { OPS_GRID_ACCENT, OPS_GRID_HIGHLIGHT, OPS_GRID_ROW } from "@oba/ui/components/ops-grid";
import { cn } from "@oba/ui/lib/utils";
import { useMenuLook } from "@oba/shared/components/app-header/menu-look";
import { Link } from "@oba/shared/libs/i18n/navigation";

export const APPEARANCE_HREF = "/settings/appearance";

/**
 * The account menu's link to Kazan's appearance settings (layout, theme and
 * background), drawn like the menu it sits in: a grid row in the top bar's
 * menu, a plain row in the classic sidebar's menu.
 */
export function AppearanceMenuItem({ onNavigate }: { onNavigate?: () => void }) {
  const t = useTranslations("sidebar");
  const isGrid = useMenuLook() === "grid";

  return (
    <DropdownMenuItem
      asChild
      className={
        isGrid
          ? cn(OPS_GRID_ROW, OPS_GRID_HIGHLIGHT, "cursor-pointer")
          : "cursor-pointer rounded-lg px-3 py-2.5"
      }
    >
      <Link href={APPEARANCE_HREF} onClick={onNavigate}>
        <Palette
          className={isGrid ? cn("size-4", OPS_GRID_ACCENT) : "text-muted-foreground h-4 w-4"}
        />
        <span>{t("appearance")}</span>
      </Link>
    </DropdownMenuItem>
  );
}
