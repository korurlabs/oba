"use client";

/**
 * Next.js Error Page
 * Automatically wraps page segments in an error boundary
 */

import { RouteError } from "@/components/layout/route-error";

export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return <RouteError error={error} reset={reset} className="min-h-screen" />;
}
