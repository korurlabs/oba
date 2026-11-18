import type { Metadata } from "next";

import { SessionView } from "@/features/sessions/components/session-view";

export const metadata: Metadata = {
  title: "Session",
};

/** One session, addressed by its Claude Code session id. */
export default async function SessionPage({ params }: { params: Promise<{ sessionId: string }> }) {
  const { sessionId } = await params;
  return <SessionView sessionId={sessionId} />;
}
