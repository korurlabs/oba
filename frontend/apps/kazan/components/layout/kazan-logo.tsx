export type KazanLogoProps = {
  className?: string;
};

/** The Korur blue of the Otak, Kalkan and Ulak wordmarks, so the apps read as one family. */
const LOGO_BLUE = "#2f8fe9";

/**
 * The Kazan wordmark, shown at the start of the top bar: a yatagan beside the
 * name. The blade curves forward with its edge on the inside of the curve, the
 * grip drops below the blade line and ends in the two splayed ears of the
 * pommel, with a bolster but no crossguard, so the silhouette reads as a
 * yatagan and not a sabre. It follows the Otak, Kalkan and Ulak wordmarks: the
 * same height, the same blue, a heavy outline with fine inner lines for the
 * mark, and a semibold serif name.
 */
export function KazanLogo({ className = "h-5 w-auto" }: KazanLogoProps) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 600 164"
      width={600}
      height={164}
      role="img"
      aria-label="Kazan"
      className={className}
    >
      <g fill="none" stroke={LOGO_BLUE} strokeLinecap="round" strokeLinejoin="round">
        {/* The whole sword in one heavy outline: blade, bolster, grip and the pommel's ears. */}
        <path
          strokeWidth={7}
          d="M71 86C79 66 93 47 112 33C131 19 151 16 172 17C146 28 104 52 84 90L88 91L86 99L80 98C77 110 76 115 74 126C74 131 77 135 82 142C85 145 82 150 77 148C70 145 69 135 65 132C60 132 54 141 47 140C42 140 42 133 46 132C54 129 58 126 61 122C65 110 66 108 69 96L64 95L66 86Z"
        />
        {/* The fuller along the spine and the bolster's ring, as fine as the Kalkan shield's inner lines. */}
        <path strokeWidth={3} d="M79 89C87 72 100 54 118 41C129 33 136 29 142 26M65 90.5L87 95" />
        {/* The two rivets that hold the grip plates. */}
        <circle cx={73} cy={105} r={3} fill={LOGO_BLUE} stroke="none" />
        <circle cx={71} cy={116} r={3} fill={LOGO_BLUE} stroke="none" />
      </g>
      <text
        x={214}
        y={135}
        fill={LOGO_BLUE}
        fontFamily="Georgia, 'Times New Roman', serif"
        fontSize={128}
        fontWeight={600}
      >
        Kazan
      </text>
    </svg>
  );
}
