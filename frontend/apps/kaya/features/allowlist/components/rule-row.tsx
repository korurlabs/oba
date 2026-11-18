import { Badge } from "@oba/ui/sky/badge";
import { ruleText } from "@/lib/allowlist";
import { cn } from "@/lib/utils";
import type { AllowRule, PermissionEffect } from "@/types/run";

const EFFECT_TONES: Readonly<
  Record<PermissionEffect, { label: string; variant: "success" | "warning" | "error" }>
> = {
  allow: { label: "Allow", variant: "success" },
  ask: { label: "Ask", variant: "warning" },
  deny: { label: "Deny", variant: "error" },
};

export function EffectBadge({ effect }: { effect: PermissionEffect }) {
  const tone = EFFECT_TONES[effect];
  return <Badge variant={tone.variant}>{tone.label}</Badge>;
}

export type RuleRowProps = {
  rule: AllowRule;
  /** Shows the tool around the pattern, for lists that mix tools. */
  full?: boolean;
  className?: string;
};

/** One rule: its pattern in monospace and its effect. */
export function RuleRow({ rule, full = false, className }: RuleRowProps) {
  return (
    <div className={cn("flex flex-wrap items-center justify-between gap-2 py-2.5", className)}>
      <code className="font-mono text-sm break-all">
        {full ? ruleText(rule) : (rule.pattern ?? `${rule.tool} (every call)`)}
      </code>
      <EffectBadge effect={rule.effect} />
    </div>
  );
}
