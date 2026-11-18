import type { Metadata } from "next";
import { Suspense } from "react";

import { SessionWall, WallLoading } from "@/features/sessions/components";

export const metadata: Metadata = {
  title: "Sessions",
};

/** The wall of the organization's sessions; its filters are read from the URL in the browser. */
export default function SessionsPage() {
  return (
    <Suspense fallback={<WallLoading />}>
      <SessionWall />
    </Suspense>
  );
}
