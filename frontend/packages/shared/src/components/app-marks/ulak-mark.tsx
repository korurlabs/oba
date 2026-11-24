import { MARK_BLUE, viewBoxOf, type AppMarkProps, type MarkBox } from "./app-mark";

/** The messengers' place in the Ulak wordmark: the node and the planes with their heavy strokes. */
export const ULAK_MARK_BOX: MarkBox = { x: 18, y: 8, width: 160, height: 140 };

/**
 * The Ulak mark: a relay node that sends three paper planes out on curved
 * trails, the messengers that carry an organization's work together. Heavy
 * outlines with fine inner lines, like the other marks.
 */
export function UlakMark(props: AppMarkProps) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox={viewBoxOf(ULAK_MARK_BOX)}
      width={ULAK_MARK_BOX.width}
      height={ULAK_MARK_BOX.height}
      aria-hidden="true"
      focusable="false"
      {...props}
    >
      <g fill="none" stroke={MARK_BLUE} strokeLinecap="round" strokeLinejoin="round">
        {/* The relay node the messengers leave from, with a solid hub. */}
        <circle cx={38} cy={128} r={16} strokeWidth={7} />
        <circle cx={38} cy={128} r={5} fill={MARK_BLUE} stroke="none" />
        {/* Three paper planes flying in formation, drawn as heavy as the Kalkan shield's edge. */}
        <path
          strokeWidth={7}
          d="M100 12L55 40L79 39L85 63ZM158 34L108 52L133 56L133 81ZM174 86L123 101L147 107L146 131Z"
        />
        {/* Each plane's centre fold, as fine as the Kalkan shield's inner lines. */}
        <path strokeWidth={3} d="M100 12L79 39M158 34L133 56M174 86L147 107" />
        {/* The dashed trails that curve from the node to each plane. */}
        <path
          strokeWidth={3}
          strokeDasharray="9 9"
          d="M47 103Q62 61 74 46M60 113Q101 84 126 62M65 127Q123 125 140 112"
        />
      </g>
    </svg>
  );
}
