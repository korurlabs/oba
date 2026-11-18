import { cn } from "@/lib/utils";
import { DEFAULT_BOARD_COLOR } from "@/features/boards/lib/board-colors";
import { normalizeHexColor } from "@/lib/utils/color";

const colorMap: Record<string, string> = {
  "#4AA3FF": "bg-korur-blue",
  "#FFB7C5": "bg-[#FFB7C5]",
  "#C084FC": "bg-purple-400",
  "#60A5FA": "bg-blue-400",
  "#4ADE80": "bg-green-400",
  "#FBBF24": "bg-amber-400",
  "#9CA3AF": "bg-gray-400",
};

/**
 * Palette colours keep their theme classes; any other valid hex (a custom
 * board colour) is drawn as it is, and an unusable value falls back to Korur blue.
 */
export function boardDotAppearance(color: string | null | undefined): {
  className?: string;
  style?: { backgroundColor: string };
} {
  const paletteClass = color ? colorMap[color.toUpperCase()] : undefined;
  if (paletteClass) return { className: paletteClass };
  const hex = normalizeHexColor(color);
  if (hex) return { style: { backgroundColor: hex } };
  return { className: "bg-korur-blue" };
}

export function BoardDot({ color = DEFAULT_BOARD_COLOR }: { color?: string | null }) {
  const appearance = boardDotAppearance(color);
  return (
    <div className="flex w-5 shrink-0 items-center justify-center">
      <div
        className={cn("h-2 w-2 shrink-0 rounded-full", appearance.className)}
        style={appearance.style}
      />
    </div>
  );
}
