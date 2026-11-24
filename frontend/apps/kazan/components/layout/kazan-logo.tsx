import { KAZAN_MARK_BOX, KazanMark } from "@oba/shared/components/app-marks/kazan-mark";

export type KazanLogoProps = {
  className?: string;
};

/** The Korur blue of the Otak, Kalkan and Ulak wordmarks, so the apps read as one family. */
const LOGO_BLUE = "#2f8fe9";

/**
 * The Kazan wordmark, shown at the start of the top bar: the shared eagle
 * mark beside the name. It follows the Otak, Kalkan and Ulak wordmarks: the
 * same height, the same blue and a semibold serif name. The wingspan makes
 * the mark wider than theirs, so the name starts further right with the same
 * gap.
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
      <KazanMark {...KAZAN_MARK_BOX} />
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
