import type { Metadata } from "next";
import { Suspense } from "react";

import { PathMappingPage } from "@/features/path-mappings/components";

export const metadata: Metadata = {
  title: "Path mapping",
};

/** Where every kind lives in a repository; the scope lives in the URL. */
export default function PathMappingRoute() {
  return (
    <Suspense>
      <PathMappingPage />
    </Suspense>
  );
}
