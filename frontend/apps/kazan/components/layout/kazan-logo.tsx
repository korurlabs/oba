export type KazanLogoProps = {
  className?: string;
};

/** The Korur blue of the Otak, Kalkan and Ulak wordmarks, so the apps read as one family. */
const LOGO_BLUE = "#2f8fe9";

/**
 * The Kazan wordmark, shown at the start of the top bar: a steppe eagle
 * soaring with its wings fully spread, seen from below, its head turned in
 * profile toward the name. Five fingered primaries on each raised wing, the
 * fanned tail and the hooked beak carry the silhouette, so it still reads as
 * an eagle in flight at header height. It follows the Otak, Kalkan and Ulak
 * wordmarks: the same height, the same blue, a heavy outline with fine inner
 * lines for the mark, and a semibold serif name. The wingspan makes the mark
 * wider than theirs, so the name starts further right with the same gap.
 */
export function KazanLogo({ className = "h-5 w-auto" }: KazanLogoProps) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 726 164"
      width={726}
      height={164}
      role="img"
      aria-label="Kazan"
      className={className}
    >
      <g fill="none" stroke={LOGO_BLUE} strokeLinecap="round" strokeLinejoin="round">
        {/* The whole bird in one heavy outline: fingered wings, scalloped trailing edges, fanned tail and the head in profile. */}
        <path
          strokeWidth={7}
          d="M147 58C147.5 61 144 66 138 66C122 64 108 52 94 38C91.7 36.5 89.3 35.1 87 33.6C79.1 24.5 71.4 13.8 67.3 10.9C63.2 5.7 55.3 11.8 59.4 17.1C62 21.1 64.4 25.8 67.7 31.6C57.8 25.8 50.7 20.8 43 17.2C37.5 13.4 31.8 21.5 37.2 25.4C43.9 30.5 50.4 36.4 59.3 43.7C46.6 40.6 37.2 37.4 27.6 35.9C21.2 33.9 18.1 43.4 24.5 45.4C33.5 48.8 42.6 52.8 54.7 57.7C43.3 58.5 34.5 58.3 26.1 59.6C19.5 59.5 19.3 69.5 25.9 69.6C34.3 70.2 43 71.4 54.4 72.5C48.3 75.3 43.4 76.6 39.2 79.4C32.8 81.2 35.5 90.8 41.9 89C46.7 88.1 56 85.9 65.4 83.8C69.2 87.8 79.6 96.6 84.3 92.6C88.1 96.6 98.5 102.9 103.2 98.9C107 102.9 117.4 105.7 122.1 101.7C125.9 105.7 136.3 106 141 102C142 108 144 112 145 116C140 126 134 136 129 146C132 152 138 152 141 149C144 153 150 154 152 150C153 153 155 154 156 154C157 154 159 153 160 150C162 154 168 153 171 149C174 152 180 152 183 146C178 136 172 126 167 116C168 112 170 108 171 102C175.7 106 186.1 105.7 189.9 101.7C194.6 105.7 205 102.9 208.8 98.9C213.5 102.9 223.9 96.6 227.7 92.6C232.4 96.6 242.8 87.8 246.6 83.8C256 85.9 265.3 88.1 270.1 89C276.5 90.8 279.2 81.2 272.8 79.4C268.6 76.6 263.7 75.3 257.6 72.5C269 71.4 277.7 70.2 286.1 69.6C292.7 69.5 292.5 59.5 285.9 59.6C277.5 58.3 268.7 58.5 257.3 57.7C269.4 52.8 278.5 48.8 287.5 45.4C293.9 43.4 290.8 33.9 284.4 35.9C274.8 37.4 265.4 40.6 252.7 43.7C261.6 36.4 268.1 30.5 274.8 25.4C280.2 21.5 274.5 13.4 269 17.2C261.3 20.8 254.2 25.8 244.3 31.6C247.6 25.8 250 21.1 252.6 17.1C256.7 11.8 248.8 5.7 244.7 10.9C240.6 13.8 232.9 24.5 225 33.6C222.7 35.1 220.3 36.5 218 38C204 52 190 64 174 66C168 66 164.5 61 165 58C167.5 43.1 169.8 38.5 174.4 35.1C177.9 32.8 181.3 31.6 185.9 31.6C190.5 31.6 195.1 31.6 198.6 32.8C200.9 33.9 202 36.2 200.9 39.7C205.5 36.2 207.8 29.3 205.5 24.7C203.2 20.1 197.4 16.7 189.4 15.5C184.8 14.4 182.5 13.2 180.2 12.1C173.3 8.6 162.9 7.5 154.9 9.8C148 12.1 143.4 17.8 142.2 25.9C141.1 35.1 146 54 147 58Z"
        />
        {/* The underwing coverts, the breast and the tail feathers, as fine as the Kalkan shield's inner lines. */}
        <path
          strokeWidth={3}
          d="M132 86C118 80 104 72 92 72M180 86C194 80 208 72 220 72M147 70C141 80 140 94 144 106M165 70C171 80 172 94 168 106M150 124C148 132 146 140 144 146M162 124C164 132 166 140 168 146"
        />
        {/* The brow over the eye and the base of the beak. */}
        <path
          strokeWidth={3}
          d="M165.2 17.2C169.8 14.9 176.7 14.9 182.5 17.2M189.4 16.1C187.1 21.3 187.1 27 188.2 31.6"
        />
        <circle cx={173.4} cy={23.1} r={3.6} fill={LOGO_BLUE} stroke="none" />
      </g>
      <text
        x={340}
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
