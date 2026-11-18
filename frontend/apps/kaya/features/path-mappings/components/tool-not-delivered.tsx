import { useId } from "react";

import { InfoTip } from "@oba/ui/sky/info-tip";

import type { ToolGapResponse } from "../schemas";

export type ToolNotDeliveredProps = {
  /** The tool's name, as the heading of the view shows it. */
  toolName: string;
  gaps: readonly ToolGapResponse[];
};

/**
 * What the tool reads that Kaya does not deliver, so a reader knows which of
 * its inputs stay as each repository or machine has them.
 */
export function ToolNotDelivered({ toolName, gaps }: ToolNotDeliveredProps) {
  const headingId = useId();
  return (
    <section aria-labelledby={headingId} className="flex flex-col gap-2">
      <div className="flex items-center gap-2">
        <h3 id={headingId} className="text-base font-semibold">
          Not delivered by Kaya
        </h3>
        <InfoTip label="What is not delivered by Kaya?">
          {toolName} also reads these paths. Kaya does not deliver them, so they stay as each
          repository or machine has them.
        </InfoTip>
      </div>
      {gaps.length === 0 ? (
        <p className="text-muted-foreground text-sm">Kaya delivers every path {toolName} reads.</p>
      ) : (
        <ul className="divide-y rounded-md border">
          {gaps.map((gap: ToolGapResponse) => (
            <li key={gap.path} className="flex flex-col gap-1 p-3 sm:flex-row sm:gap-4">
              <code className="font-mono text-xs break-all sm:w-56 sm:shrink-0">{gap.path}</code>
              <span className="text-muted-foreground text-sm">{gap.note}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
