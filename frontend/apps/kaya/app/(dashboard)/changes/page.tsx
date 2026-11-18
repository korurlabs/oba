import type { Metadata } from "next";
import { Suspense } from "react";

import { ChangesPage } from "@/features/changes/components";

export const metadata: Metadata = {
  title: "Changes",
};

/** The organization's config changes; the filters live in the URL, which needs a Suspense boundary. */
export default function ChangesRoute() {
  return (
    <Suspense>
      <ChangesPage />
    </Suspense>
  );
}
