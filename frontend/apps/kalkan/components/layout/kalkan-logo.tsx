import { KALKAN_MARK_BOX, KalkanMark } from "@oba/shared/components/app-marks/kalkan-mark";

export type KalkanLogoProps = {
  className?: string;
};

/** The Korur blue of the Otak wordmark, so the two apps read as one family. */
const LOGO_BLUE = "#2f8fe9";

/**
 * The Kalkan wordmark, the shared shield mark beside the name, shown at the
 * start of the top bar. It follows the Otak wordmark: the same height, the
 * same blue and a semibold serif name.
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
      <KalkanMark {...KALKAN_MARK_BOX} />
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
