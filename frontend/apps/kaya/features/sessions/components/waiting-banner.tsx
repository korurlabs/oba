import { Hand } from "lucide-react";

import { Markdown } from "./markdown";

export type WaitingBannerProps = {
  /** The session's last assistant message, which usually holds the question. */
  message: string | null;
};

/**
 * Tells the user that Claude's turn ended and the session waits for their
 * answer, with the last thing it said.
 */
export function WaitingBanner({ message }: WaitingBannerProps) {
  return (
    <section
      role="status"
      aria-label="Waiting for you"
      className="border-warning/60 bg-warning-light flex flex-col gap-3 rounded-xl border p-5"
    >
      <div className="flex flex-wrap items-center gap-2">
        <Hand className="text-warning-text size-5" aria-hidden />
        <h2 className="text-lg font-semibold">Waiting for you</h2>
      </div>
      {message ? (
        <Markdown source={message} className="max-h-64 overflow-y-auto" />
      ) : (
        <p className="text-muted-foreground text-sm">The last message was not recorded.</p>
      )}
    </section>
  );
}
