import type { Metadata } from "next";
import { Suspense } from "react";

import { ConventionDetail } from "@/features/conventions/components";

export const metadata: Metadata = {
  title: "New convention",
};

/** A new convention doc, in the viewer's open draft. */
export default function NewConventionDocRoute() {
  return (
    <Suspense>
      <ConventionDetail path={null} />
    </Suspense>
  );
}
