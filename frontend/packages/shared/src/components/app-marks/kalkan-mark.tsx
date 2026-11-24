import { MARK_BLUE, viewBoxOf, type AppMarkProps, type MarkBox } from "./app-mark";

/** The shield's place in the Kalkan wordmark: its outer edge with the heavy stroke around it. */
export const KALKAN_MARK_BOX: MarkBox = { x: 37, y: 14, width: 132, height: 147 };

/** The Kalkan mark: a line-drawn shield with an inner rim and a quartering cross. */
export function KalkanMark(props: AppMarkProps) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox={viewBoxOf(KALKAN_MARK_BOX)}
      width={KALKAN_MARK_BOX.width}
      height={KALKAN_MARK_BOX.height}
      aria-hidden="true"
      focusable="false"
      {...props}
    >
      <g fill="none" stroke={MARK_BLUE} strokeLinecap="round" strokeLinejoin="round">
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
    </svg>
  );
}
