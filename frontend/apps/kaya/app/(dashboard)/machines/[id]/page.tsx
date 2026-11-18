import type { Metadata } from "next";

import { MachineDetail } from "@/features/machines/components";

export const metadata: Metadata = {
  title: "Machine",
};

/** One machine: its user, token, usage and actions. */
export default async function MachineRoute({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <MachineDetail machineId={id} />;
}
