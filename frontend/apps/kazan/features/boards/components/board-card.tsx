"use client";

import { Link } from "@oba/shared/libs/i18n/navigation";
import { MoreVertical } from "lucide-react";
import { Button } from "@/components/ui/button";
import { boardPath } from "@/features/boards/lib/board-route";
import { useTranslations } from "next-intl";

interface BoardCardProps {
  id: string;
  slug: string;
  name: string;
  prefix: string;
  color?: string;
  icon?: string | null;
}

const colorToGradient: Record<string, string> = {
  "#4AA3FF": "from-korur-blue-medium to-korur-blue",
  "#FFB7C5": "from-[#FFD4E0] to-[#FFB7C5]",
  "#C084FC": "from-purple-200 to-purple-400",
  "#60A5FA": "from-blue-200 to-blue-400",
  "#4ADE80": "from-green-200 to-green-400",
  "#FBBF24": "from-amber-200 to-amber-400",
  "#9CA3AF": "from-gray-200 to-gray-400",
};

function getGradientClass(color?: string): string {
  if (color && colorToGradient[color]) {
    return colorToGradient[color];
  }
  return "from-korur-blue-medium to-korur-blue";
}

export function BoardCard({ slug, name, prefix, color }: BoardCardProps) {
  const gradientClass = getGradientClass(color);

  return (
    <Link href={boardPath(slug)}>
      <div className="group bg-card text-card-foreground border-border relative cursor-pointer overflow-hidden rounded-2xl border transition-all duration-150 ease-in-out hover:-translate-y-1 hover:border-transparent hover:shadow-lg">
        {/* Gradient Bar */}
        <div className={`h-2 bg-gradient-to-r ${gradientClass}`} />

        {/* Content */}
        <div className="p-5">
          <div className="mb-4 flex items-start justify-between">
            <div className="min-w-0 flex-1">
              <h3 className="text-foreground group-hover:text-primary truncate text-base font-semibold transition-colors">
                {name}
              </h3>
              <p className="text-muted-foreground mt-1 text-sm">{prefix}</p>
            </div>

            <Button
              variant="ghost"
              size="icon-sm"
              className="-mr-2 opacity-0 transition-opacity group-hover:opacity-100"
              onClick={(e) => {
                e.preventDefault();
                e.stopPropagation();
              }}
            >
              <MoreVertical className="text-muted-foreground h-4 w-4" />
            </Button>
          </div>
        </div>
      </div>
    </Link>
  );
}

/**
 * Create Board Card - Special card for creating new boards
 */
export function CreateBoardCard({ onClick }: { onClick: () => void }) {
  const t = useTranslations("board");

  return (
    <div
      onClick={onClick}
      className="group bg-card border-border hover:border-primary hover:bg-primary/10 relative flex h-[180px] cursor-pointer flex-col items-center justify-center overflow-hidden rounded-2xl border-2 border-dashed transition-all duration-200"
    >
      {/* Icon */}
      <div className="bg-muted group-hover:bg-primary/20 mb-3 flex h-12 w-12 items-center justify-center rounded-xl transition-colors">
        <svg
          xmlns="http://www.w3.org/2000/svg"
          width="24"
          height="24"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          className="text-muted-foreground group-hover:text-primary transition-colors"
        >
          <path d="M5 12h14" />
          <path d="M12 5v14" />
        </svg>
      </div>

      {/* Text */}
      <h3 className="text-foreground/80 group-hover:text-primary text-base font-semibold transition-colors">
        {t("create")}
      </h3>
      <p className="text-muted-foreground mt-1 text-sm">{t("startNewProject")}</p>
    </div>
  );
}
