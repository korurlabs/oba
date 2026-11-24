export type AccountLogoProps = {
  className?: string;
};

/** The Korur blue of the Otak, Kalkan, Ulak and Kazan wordmarks, so the apps read as one family. */
const LOGO_BLUE = "#2f8fe9";

/**
 * The Account wordmark, shown at the start of the top bar: a calm bust of a
 * person, the one whose profile, invitations and settings this app holds,
 * beside the name. The head and the shoulders are two heavy outlines with a
 * clear gap, so the figure still reads at header height; a fine open collar
 * and placket give it a little character from twice that size. It follows the
 * Otak, Kalkan, Ulak and Kazan wordmarks: the same height, the same blue, a
 * heavy outline with fine inner lines for the mark, and a semibold serif name.
 */
export function AccountLogo({ className = "h-5 w-auto" }: AccountLogoProps) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 710 164"
      width={710}
      height={164}
      role="img"
      aria-label="Account"
      className={className}
    >
      <g fill="none" stroke={LOGO_BLUE} strokeLinecap="round" strokeLinejoin="round">
        {/* The head and the shoulders, as heavy as the shield's outer edge. */}
        <path
          strokeWidth={7}
          d="M103 23a29 29 0 1 1 0 58a29 29 0 1 1 0-58ZM41 155C41 120 64 97 103 97C142 97 165 120 165 155Z"
        />
        {/* The open collar and the placket, as fine as the shield's inner lines. */}
        <path strokeWidth={3} d="M84 100L103 125L122 100M103 125V155" />
      </g>
      <text
        x={214}
        y={135}
        fill={LOGO_BLUE}
        fontFamily="Georgia, 'Times New Roman', serif"
        fontSize={128}
        fontWeight={600}
      >
        Account
      </text>
    </svg>
  );
}
