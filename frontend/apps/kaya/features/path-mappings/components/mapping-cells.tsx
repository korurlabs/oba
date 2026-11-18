"use client";

import { ChevronDown, ChevronRight } from "lucide-react";
import { useId, useState, type ReactNode } from "react";

import { InfoTip } from "@oba/ui/sky/info-tip";

import { notDeliveredLabel } from "./mapping-groups";

/** A quiet dash for a count the page cannot tell or a path the scope delivers nothing for. */
export const NO_COUNT = "-";

/** The lines of an info tip, one paragraph each. */
export function InfoLines({ lines }: { lines: readonly string[] }) {
  return (
    <span className="flex flex-col gap-1">
      {lines.map((line: string) => (
        <span key={line}>{line}</span>
      ))}
    </span>
  );
}

/** A path in code type, with an info tip when there is more to say about it. */
export function PathCell({ path, info }: { path: string; info: readonly string[] }) {
  return (
    <span className="inline-flex items-start gap-1.5">
      <code className="font-mono text-xs break-all">{path}</code>
      {info.length > 0 && (
        <InfoTip label={`About ${path}`}>
          <InfoLines lines={info} />
        </InfoTip>
      )}
    </span>
  );
}

/** A table heading with an info tip beside it. */
export function HeadingWithInfo({
  children,
  label,
  info,
}: {
  children: ReactNode;
  label: string;
  info: ReactNode;
}) {
  return (
    <span className="inline-flex items-center gap-1.5">
      {children}
      <InfoTip label={label}>{info}</InfoTip>
    </span>
  );
}

/**
 * The rows a scope delivers nothing for, behind one plain disclosure button
 * that starts closed, so the rows that matter come first.
 */
export function NotDeliveredGroup({ count, children }: { count: number; children: ReactNode }) {
  const [open, setOpen] = useState(false);
  const panelId = useId();
  if (count === 0) return null;
  const Chevron = open ? ChevronDown : ChevronRight;
  return (
    <div className="flex flex-col gap-2">
      <button
        type="button"
        aria-expanded={open}
        aria-controls={panelId}
        onClick={() => setOpen((value: boolean) => !value)}
        className="text-muted-foreground hover:text-foreground focus-visible:ring-ring inline-flex w-fit items-center gap-1 rounded-sm text-sm outline-none focus-visible:ring-2"
      >
        <Chevron className="size-4" aria-hidden />
        {notDeliveredLabel(count)}
      </button>
      <div id={panelId} hidden={!open} className="text-muted-foreground">
        {open && children}
      </div>
    </div>
  );
}
