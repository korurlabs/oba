export type UlakLogoProps = {
  className?: string;
};

/** The Korur blue of the Otak and Kalkan wordmarks, so the apps read as one family. */
const LOGO_BLUE = "#2f8fe9";

/**
 * The Ulak wordmark, shown at the start of the top bar: a relay node that
 * sends three paper planes out on curved trails, the messengers that carry an
 * organization's work together, beside the name. It follows the Otak and
 * Kalkan wordmarks: the same height, the same blue, heavy outlines with fine
 * inner lines for the mark, and a semibold serif name.
 */
export function UlakLogo({ className = "h-5 w-auto" }: UlakLogoProps) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 484 164"
      width={484}
      height={164}
      role="img"
      aria-label="Ulak"
      className={className}
    >
      <g fill="none" stroke={LOGO_BLUE} strokeLinecap="round" strokeLinejoin="round">
        {/* The relay node the messengers leave from, with a solid hub. */}
        <circle cx={38} cy={128} r={16} strokeWidth={7} />
        <circle cx={38} cy={128} r={5} fill={LOGO_BLUE} stroke="none" />
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
      <text
        x={214}
        y={135}
        fill={LOGO_BLUE}
        fontFamily="Georgia, 'Times New Roman', serif"
        fontSize={128}
        fontWeight={600}
      >
        Ulak
      </text>
    </svg>
  );
}
