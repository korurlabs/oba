import type { Metadata } from "next";
import { Suspense } from "react";

import { PacksPage } from "@/features/packs/components";

export const metadata: Metadata = {
  title: "Packs",
};

/** The organization's packs, their repositories and versions; the selected pack lives in the URL. */
export default function PacksRoute() {
  return (
    <Suspense>
      <PacksPage />
    </Suspense>
  );
}
