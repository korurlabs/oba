import { ULAK_MARK_BOX, UlakMark } from "@oba/shared/components/app-marks/ulak-mark";

export type UlakLogoProps = {
  className?: string;
};

/** The Korur blue of the Otak and Kalkan wordmarks, so the apps read as one family. */
const LOGO_BLUE = "#2f8fe9";

/**
 * The Ulak wordmark, shown at the start of the top bar: the shared
 * messengers mark beside the name. It follows the Otak and Kalkan wordmarks:
 * the same height, the same blue and a semibold serif name.
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
      <UlakMark {...ULAK_MARK_BOX} />
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
