export type KazanLogoProps = {
  className?: string;
};

/** The Korur blue of the Otak, Kalkan and Ulak wordmarks, so the apps read as one family. */
const LOGO_BLUE = "#2f8fe9";

/**
 * The Kazan wordmark, shown at the start of the top bar: the head of a steppe
 * eagle in profile, facing the name. The hooked beak, the heavy brow that
 * hides the top of the eye and the ragged neck feathers carry the silhouette,
 * so it still reads as an eagle at header height. It follows the Otak, Kalkan
 * and Ulak wordmarks: the same height, the same blue, a heavy outline with
 * fine inner lines for the mark, and a semibold serif name.
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
        {/* The head in one heavy outline: crown, hooked beak, throat and the ragged feathers of the neck. */}
        <path
          strokeWidth={7}
          d="M54 151C48 124 48 98 58 76C70 50 92 37 118 35C130 34 139 38 146 43C160 42 172 48 177 60C181 72 180 88 171 99C169 93 165 89 159 88C151 92 141 96 130 97C121 102 118 111 120 122C122 134 130 144 138 152L126 147L114 158L102 147L90 158L78 147L66 158Z"
        />
        {/* The brow ridge, as heavy as the outline, overhanging the eye. */}
        <path strokeWidth={7} d="M96 52C110 50 124 52 137 58" />
        {/* The eye, its top cut flat where the brow covers it. */}
        <path fill={LOGO_BLUE} stroke="none" d="M130 59.5L116 57A7.5 7.5 0 1 0 130 59.5Z" />
        {/* The base of the beak, the gape and two neck feathers, as fine as the Kalkan shield's inner lines. */}
        <path
          strokeWidth={3}
          d="M146 43C143 55 140 66 137 79M120 80C134 80 148 82 160 88M70 86C78 102 82 118 82 136M92 94C98 110 100 124 100 140"
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
        Kazan
      </text>
    </svg>
  );
}
