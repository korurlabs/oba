import type { Metadata } from "next";
import { Suspense } from "react";

import { PlansPage } from "@/features/plans/components";

export const metadata: Metadata = {
  title: "Plans",
};

/** The organization's plans; the filters live in the URL, which needs a Suspense boundary. */
export default function PlansRoute() {
  return (
    <Suspense>
      <PlansPage />
    </Suspense>
  );
}
