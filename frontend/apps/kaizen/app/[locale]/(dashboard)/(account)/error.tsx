"use client";

/**
 * Account Route Group Error Boundary
 * Wraps the account pages in an error boundary that sits inside the shell, so
 * the sidebar and the account navigation row stay on screen when a page throws.
 */

import { RouteError } from "@/components/layout/route-error";

export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return <RouteError error={error} reset={reset} className="min-h-full" />;
}
