"use client";

import { useMemo, useState } from "react";
import { Search } from "lucide-react";

import { Card, CardContent, CardHeader, CardTitle } from "@oba/ui/sky/card";
import { CONTROL_CLASS } from "@oba/ui/sky/field";
import { filterRules, groupRules, type RuleCard, type RuleSection } from "@/lib/allowlist";
import { cn } from "@/lib/utils";
import type { AllowRule } from "@/types/run";

import { RuleRow } from "./rule-row";
import { CARD_GRID, headingId } from "./styles";

export type RuleListProps = {
  rules: readonly AllowRule[];
};

function count(rules: number): string {
  return rules === 1 ? "1 rule" : `${rules} rules`;
}

/**
 * Every rule, one section per tool, Bash split into one card per program.
 * The text filter is local state, so a reload of the allowlist keeps it.
 */
export function RuleList({ rules }: RuleListProps) {
  const [text, setText] = useState("");
  const visible = useMemo(() => filterRules(rules, { source: null, text }), [rules, text]);
  const sections = useMemo(() => groupRules(visible), [visible]);

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-3 sm:flex-row">
        <div className="relative flex-1">
          <Search
            className="text-muted-foreground pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2"
            aria-hidden
          />
          <input
            type="search"
            value={text}
            onChange={(event) => setText(event.target.value)}
            placeholder="Filter rules by pattern or tool"
            aria-label="Filter rules"
            className={cn(CONTROL_CLASS, "h-10 w-full pr-3 pl-9 text-sm")}
          />
        </div>
      </div>

      {visible.length !== rules.length && (
        <p className="text-muted-foreground text-sm" role="status">
          {`${count(visible.length)} of ${rules.length} match the filter.`}
        </p>
      )}

      {sections.length === 0 ? (
        <Card className="items-center py-12 text-center">
          <CardHeader className="w-full justify-items-center">
            <CardTitle className="text-lg">
              {rules.length === 0
                ? "No permission rules are set."
                : "No rule matches these filters."}
            </CardTitle>
          </CardHeader>
        </Card>
      ) : (
        sections.map((section: RuleSection) => (
          <section
            key={section.tool}
            aria-labelledby={headingId("tool", section.tool)}
            className="flex flex-col gap-4"
          >
            <h2 id={headingId("tool", section.tool)} className="flex items-baseline gap-2 text-xl">
              {section.tool}
              <span className="text-muted-foreground text-sm font-normal">{section.count}</span>
            </h2>
            <ul className={CARD_GRID}>
              {section.cards.map((card: RuleCard) => (
                <li key={card.title}>
                  <Card className="h-full gap-2 py-4">
                    <CardHeader className="px-5">
                      <CardTitle className="flex items-baseline justify-between gap-2">
                        <span className="font-mono text-base break-all">{card.title}</span>
                        <span className="text-muted-foreground shrink-0 text-xs font-normal">
                          {count(card.rules.length)}
                        </span>
                      </CardTitle>
                    </CardHeader>
                    <CardContent className="px-5">
                      <ul className="divide-y">
                        {card.rules.map((rule: AllowRule, index: number) => (
                          <li key={`${rule.effect}:${rule.line}:${index}`}>
                            <RuleRow rule={rule} full={section.tool === "MCP"} />
                          </li>
                        ))}
                      </ul>
                    </CardContent>
                  </Card>
                </li>
              ))}
            </ul>
          </section>
        ))
      )}
    </div>
  );
}
