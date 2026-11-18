"use client";

/**
 * Dashboard Error Boundary
 * Sits inside the shell, so the header stays on screen when a page throws.
 */

import { RouteError } from "@/components/layout/route-error";

export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return <RouteError error={error} reset={reset} className="min-h-full py-16" />;
}
