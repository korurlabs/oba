import type { Metadata } from "next";
import { Suspense } from "react";

import { RepositoriesPage } from "@/features/repositories/components";

export const metadata: Metadata = {
  title: "Repositories",
};

/** The organization's repositories; the status filter lives in the URL. */
export default function RepositoriesRoute() {
  return (
    <Suspense>
      <RepositoriesPage />
    </Suspense>
  );
}
