import type { Metadata } from "next";
import { Suspense } from "react";

import { AllowlistPage } from "@/features/allowlist/components";

export const metadata: Metadata = {
  title: "Allowlist",
};

/** The permission rules of a repository or pack; the scope lives in the URL. */
export default function AllowlistRoute() {
  return (
    <Suspense>
      <AllowlistPage />
    </Suspense>
  );
}
