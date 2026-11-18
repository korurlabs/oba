import type { Metadata } from "next";
import { Suspense } from "react";

import { LessonsPage } from "@/features/lessons/components";

export const metadata: Metadata = {
  title: "Lessons",
};

/** The organization's lessons; the filters live in the URL, which needs a Suspense boundary. */
export default function LessonsRoute() {
  return (
    <Suspense>
      <LessonsPage />
    </Suspense>
  );
}
