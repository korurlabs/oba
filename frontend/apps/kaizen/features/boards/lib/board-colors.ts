/**
 * The colour palette shared by boards and milestones. Values are `#RRGGBB`,
 * the format the server checks for both.
 */
export const BOARD_COLOR_OPTIONS = [
  { name: "Korur", value: "#4AA3FF" },
  { name: "Sakura", value: "#FFB7C5" },
  { name: "Purple", value: "#C084FC" },
  { name: "Blue", value: "#60A5FA" },
  { name: "Green", value: "#4ADE80" },
  { name: "Yellow", value: "#FBBF24" },
  { name: "Gray", value: "#9CA3AF" },
] as const;

export const DEFAULT_BOARD_COLOR = BOARD_COLOR_OPTIONS[0].value;

export { HEX_COLOR_PATTERN } from "@/lib/utils/color";
