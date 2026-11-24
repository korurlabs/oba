import { MARK_BLUE, viewBoxOf, type AppMarkProps, type MarkBox } from "./app-mark";

/** The person's place in the Account wordmark: the head and shoulders with the heavy stroke around them. */
export const ACCOUNT_MARK_BOX: MarkBox = { x: 37, y: 19, width: 132, height: 140 };

/**
 * The Account mark: a calm bust of a person, the one whose profile,
 * invitations and settings the app holds. The head and the shoulders are two
 * heavy outlines with a clear gap, so the figure still reads at header
 * height; a fine open collar and placket give it a little character from
 * twice that size.
 */
export function AccountMark(props: AppMarkProps) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox={viewBoxOf(ACCOUNT_MARK_BOX)}
      width={ACCOUNT_MARK_BOX.width}
      height={ACCOUNT_MARK_BOX.height}
      aria-hidden="true"
      focusable="false"
      {...props}
    >
      <g fill="none" stroke={MARK_BLUE} strokeLinecap="round" strokeLinejoin="round">
        {/* The head and the shoulders, as heavy as the shield's outer edge. */}
        <path
          strokeWidth={7}
          d="M103 23a29 29 0 1 1 0 58a29 29 0 1 1 0-58ZM41 155C41 120 64 97 103 97C142 97 165 120 165 155Z"
        />
        {/* The open collar and the placket, as fine as the shield's inner lines. */}
        <path strokeWidth={3} d="M84 100L103 125L122 100M103 125V155" />
      </g>
    </svg>
  );
}
