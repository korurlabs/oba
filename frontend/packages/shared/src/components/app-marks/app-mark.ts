import type { ComponentType, SVGProps } from "react";

/** The Korur blue of the app marks and wordmarks, so the apps read as one family. */
export const MARK_BLUE = "#2f8fe9";

/**
 * Where a mark sits in its app's wordmark, in the wordmark's 164-unit-high
 * drawing. A wordmark nests the mark at this box, so the header logo is drawn
 * exactly as one picture; the mark's own view box is the same box, cropped to
 * the artwork and its outline, so a mark shown alone fills its height.
 */
export type MarkBox = { x: number; y: number; width: number; height: number };

/**
 * Props of a mark: any SVG attribute except the view box, which the artwork
 * fixes. A mark is decorative (`aria-hidden`) unless the caller names it.
 */
export type AppMarkProps = Omit<SVGProps<SVGSVGElement>, "viewBox">;

/** An app's mark: the picture of its header logo without the name. */
export type AppMark = ComponentType<AppMarkProps>;

/** The view box attribute of a mark's box. */
export function viewBoxOf(box: MarkBox): string {
  return `${box.x} ${box.y} ${box.width} ${box.height}`;
}
