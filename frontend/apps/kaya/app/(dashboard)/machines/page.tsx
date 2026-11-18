import type { Metadata } from "next";
import { Suspense } from "react";

import { MachinesPage } from "@/features/machines/components";

export const metadata: Metadata = {
  title: "Machines",
};

/** The organization's machines with their usage; the status filter lives in the URL. */
export default function MachinesRoute() {
  return (
    <Suspense>
      <MachinesPage />
    </Suspense>
  );
}
