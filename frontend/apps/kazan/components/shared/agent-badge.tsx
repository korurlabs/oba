"use client";

import { Bot } from "lucide-react";
import { useTranslations } from "next-intl";
import type { AgentProvider, AgentRole } from "@oba/shared/features/users/schema";
import { cn } from "@/lib/utils";

/** Vendor names are proper nouns, the same in every language. */
const PROVIDER_NAMES: Record<AgentProvider, string> = {
  ANTHROPIC: "Anthropic",
  OPENAI: "OpenAI",
  XAI: "xAI",
  DEEPSEEK: "DeepSeek",
};

interface AgentBadgeProps {
  agentRole?: AgentRole | null;
  agentProvider?: AgentProvider | null;
  className?: string;
}

/**
 * Marks a member, candidate or assignee as an agent, so a person never
 * mistakes an agent for a colleague when assigning work or managing a board.
 * A `span`, so it may sit inside a picker option's button.
 */
export function AgentBadge({ agentRole, agentProvider, className }: AgentBadgeProps) {
  const t = useTranslations("kazan.agents");
  const details = [
    agentRole ? t(`roles.${agentRole}`) : null,
    agentProvider ? PROVIDER_NAMES[agentProvider] : null,
  ].filter((detail): detail is string => detail !== null);

  return (
    <span className={cn("inline-flex min-w-0 shrink-0 items-center gap-1.5 text-xs", className)}>
      <span className="border-primary/40 text-korur-blue-strong dark:text-korur-blue inline-flex shrink-0 items-center gap-0.5 rounded-md border px-1.5 py-0 text-[10px] leading-4 font-semibold">
        <Bot className="h-2.5 w-2.5" aria-hidden />
        {t("badge")}
      </span>
      {details.length > 0 && (
        <span className="text-muted-foreground truncate">{details.join(" · ")}</span>
      )}
    </span>
  );
}
