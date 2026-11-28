"use client";

import { useMemo } from "react";

import { Card, CardContent, CardHeader, CardTitle } from "@oba/ui/sky/card";
import { ListEmptyState } from "@oba/ui/sky/page-states";
import { CLEAR_FILTERS_LABEL } from "@/components/common/list-parts";
import { filterRules, groupRules, type RuleCard, type RuleSection } from "@/lib/allowlist";
import type { AllowRule } from "@/types/run";

import { RuleRow } from "./rule-row";
import { CARD_GRID, headingId } from "./styles";

export type RuleListProps = {
  rules: readonly AllowRule[];
  /** The toolbar's search on pattern and tool; "" keeps every rule. */
  search: string;
  /** Empties the toolbar's search. */
  onClearSearch: () => void;
};

function count(rules: number): string {
  return rules === 1 ? "1 rule" : `${rules} rules`;
}

/** Every rule, one section per tool, Bash split into one card per program. */
export function RuleList({ rules, search, onClearSearch }: RuleListProps) {
  const visible = useMemo(
    () => filterRules(rules, { source: null, text: search }),
    [rules, search]
  );
  const sections = useMemo(() => groupRules(visible), [visible]);

  return (
    <div className="flex flex-col gap-6">
      {visible.length !== rules.length && (
        <p className="text-muted-foreground text-sm" role="status">
          {`${count(visible.length)} of ${rules.length} match the filter.`}
        </p>
      )}

      {sections.length === 0 ? (
        <ListEmptyState
          filtered={rules.length > 0}
          onClearFilters={onClearSearch}
          labels={{
            emptyTitle: "No permission rules are set",
            emptyDescription:
              "Rules show here once the settings file allows, asks or denies a tool.",
            filteredTitle: "No rule matches",
            filteredDescription: "No rule matches the search.",
            clearFilters: CLEAR_FILTERS_LABEL,
          }}
        />
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
