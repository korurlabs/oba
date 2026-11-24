"use client";

import { useState } from "react";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";

const DEFAULT_COLOR = "#6B7280";

type LabelBadgeProps = {
  label: { name: string; color?: string | null };
  size?: "sm" | "md";
};

type LabelBadgeListProps = {
  labels: { id: string; name: string; color?: string | null }[];
  maxVisible?: number;
  size?: "sm" | "md";
};

export function LabelBadge({ label, size = "md" }: LabelBadgeProps) {
  const color = label.color || DEFAULT_COLOR;

  const sizeClasses =
    size === "sm"
      ? "text-[10px] px-1.5 py-0.5 gap-0.5"
      : "text-xs px-2 py-0.5 gap-1";

  const dotClasses = size === "sm" ? "h-1.5 w-1.5" : "h-2 w-2";

  return (
    <span
      className={`inline-flex items-center font-medium border ${sizeClasses}`}
      style={{ borderColor: `${color}40` }}
    >
      <span
        className={`shrink-0 ${dotClasses}`}
        style={{ backgroundColor: color }}
      />
      {label.name}
    </span>
  );
}

function CompactLabelDots({
  labels,
  remaining,
  overflowTextClass,
}: {
  labels: { id: string; name: string; color?: string | null }[];
  remaining: number;
  overflowTextClass: string;
}) {
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);

  return (
    <TooltipProvider>
      <div className="flex items-center -space-x-1.5">
        {labels.map((label, index) => {
          const color = label.color || DEFAULT_COLOR;
          const isHovered = hoveredIndex === index;
          return (
            <Tooltip key={label.id}>
              <TooltipTrigger asChild>
                <span
                  className="ring-background relative h-3.5 w-3.5 shrink-0 cursor-default ring-2 transition-[box-shadow,ring-color] duration-150 hover:z-10"
                  style={{
                    backgroundColor: color,
                    zIndex: isHovered ? 10 : index,
                    ...(isHovered ? { "--tw-ring-color": color } as React.CSSProperties : {}),
                  }}
                  onMouseEnter={() => setHoveredIndex(index)}
                  onMouseLeave={() => setHoveredIndex(null)}
                />
              </TooltipTrigger>
              <TooltipContent>
                <p>{label.name}</p>
              </TooltipContent>
            </Tooltip>
          );
        })}
        {remaining > 0 && (
          <span
            className={`text-muted-foreground pl-2.5 font-medium ${overflowTextClass}`}
          >
            +{remaining}
          </span>
        )}
      </div>
    </TooltipProvider>
  );
}

export function LabelBadgeList({
  labels,
  maxVisible = 3,
  size = "md",
}: LabelBadgeListProps) {
  if (!labels || labels.length === 0) {
    return null;
  }

  const visibleLabels = labels.slice(0, maxVisible);
  const remaining = labels.length - maxVisible;

  const overflowTextClass = size === "sm" ? "text-[10px]" : "text-xs";

  const isCompact = size === "sm" && labels.length > 1;

  return (
    <div className="flex flex-wrap items-center gap-1">
      {isCompact ? (
        <CompactLabelDots
          labels={visibleLabels}
          remaining={remaining}
          overflowTextClass={overflowTextClass}
        />
      ) : (
        visibleLabels.map((label) => (
          <LabelBadge key={label.id} label={label} size={size} />
        ))
      )}
      {!isCompact && remaining > 0 && (
        <span
          className={`text-muted-foreground inline-flex items-center font-medium ${overflowTextClass}`}
        >
          +{remaining}
        </span>
      )}
    </div>
  );
}
