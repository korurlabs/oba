/**
 * Whether the user asked the operating system for reduced motion. Decorative
 * movement (sliding rows, a swiped message) is skipped when this is true;
 * state changes still show, only without the travel.
 *
 * Read at the moment of use rather than subscribed to, so a change of the
 * setting applies to the next animation without a re-render.
 */
export function prefersReducedMotion(): boolean {
  return (
    typeof window !== "undefined" && window.matchMedia("(prefers-reduced-motion: reduce)").matches
  );
}
