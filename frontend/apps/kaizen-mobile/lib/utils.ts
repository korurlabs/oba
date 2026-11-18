import { clsx, type ClassValue } from "clsx";
import { extendTailwindMerge } from "tailwind-merge";

/**
 * The type scale of tailwind.config.js. tailwind-merge only knows the default
 * size names (`text-sm`, `text-base`), so without this list it reads
 * `text-body` as a text colour and drops it, or `text-foreground`, whichever
 * comes first.
 */
const FONT_SIZES = [
  "large-title",
  "title-1",
  "title-2",
  "title-3",
  "headline",
  "body",
  "callout",
  "subhead",
  "footnote",
  "caption",
  "caption-2",
  "button",
  "meta",
  "badge",
  "ticket-key",
  "code",
];

const twMerge = extendTailwindMerge({
  extend: {
    classGroups: {
      "font-size": [{ text: FONT_SIZES }],
      // `border-hairline` is a width, not a border colour.
      "border-w": [{ border: ["hairline"] }],
    },
  },
});

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
