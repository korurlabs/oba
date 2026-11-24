export type KalkanLogoProps = {
  className?: string;
};

/** The Korur blue of the Otak wordmark, so the two apps read as one family. */
const LOGO_BLUE = "#2f8fe9";

/**
 * The Kalkan wordmark, a line-drawn shield beside the name, shown at the
 * start of the top bar. It follows the Otak wordmark: the same height, the
 * same blue, a heavy outline with fine inner lines for the mark, and a
 * semibold serif name.
 */
export function KalkanLogo({ className = "h-5 w-auto" }: KalkanLogoProps) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 660 164"
      width={660}
      height={164}
      role="img"
      aria-label="Kalkan"
      className={className}
    >
      <g fill="none" stroke={LOGO_BLUE} strokeLinecap="round" strokeLinejoin="round">
        {/* The shield's outer edge, drawn as heavy as the yurt's outline. */}
        <path
          strokeWidth={7}
          d="M103 18C128 31 148 35 165 35V82C165 121 139 143 103 157C67 143 41 121 41 82V35C58 35 78 31 103 18Z"
        />
        {/* An inner rim and a quartering cross, as fine as the yurt's lattice. */}
        <path
          strokeWidth={3}
          d="M103 32C123 42 139 46 152 46V82C152 113 132 131 103 143C74 131 54 113 54 82V46C67 46 83 42 103 32Z"
        />
        <path strokeWidth={3} d="M103 32V143M54 80H152" />
      </g>
      <text
        x={214}
        y={135}
        fill={LOGO_BLUE}
        fontFamily="Georgia, 'Times New Roman', serif"
        fontSize={128}
        fontWeight={600}
      >
        Kalkan
      </text>
    </svg>
  );
}
