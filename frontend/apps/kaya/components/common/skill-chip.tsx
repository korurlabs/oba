import Link from "next/link";

import { Badge } from "@oba/ui/sky/badge";
import { cn } from "@/lib/utils";

export type SkillChipProps = {
  name: string;
  /** Preloaded by the subagent's agent file rather than loaded by a call: drawn outlined. */
  preloaded?: boolean;
  /** The agent that loaded it has not stopped yet: drawn with a pulsing dot. */
  inUse?: boolean;
  /** Makes the chip a link, for example to the wall filtered by this skill. */
  href?: string;
  className?: string;
};

/**
 * One skill as a small monospace chip. The session view, the
 * timeline, the catalogue and the wall's skill filter all show skills with it,
 * so a skill looks the same wherever it appears.
 */
export function SkillChip({
  name,
  preloaded = false,
  inUse = false,
  href,
  className,
}: SkillChipProps) {
  const title = [name, preloaded ? "Preloaded" : null, inUse ? "In use" : null]
    .filter(Boolean)
    .join(" · ");
  const content = (
    <>
      {inUse && <span aria-hidden className="bg-info size-1.5 animate-pulse rounded-full" />}
      <span className="truncate">{name}</span>
      {inUse && <span className="sr-only">, in use</span>}
      {preloaded && <span className="sr-only">, preloaded</span>}
    </>
  );
  const chipClass = cn(
    "max-w-full font-mono font-normal",
    preloaded ? "border-dashed bg-transparent" : "bg-korur-blue-light border-transparent",
    className
  );

  if (href !== undefined) {
    return (
      <Badge asChild variant="outline" className={chipClass} title={title}>
        <Link href={href}>{content}</Link>
      </Badge>
    );
  }
  return (
    <Badge variant="outline" className={chipClass} title={title}>
      {content}
    </Badge>
  );
}
