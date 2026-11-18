import { Card, CardContent, CardHeader, CardTitle } from "@oba/ui/sky/card";

import type { Violation } from "../schemas";

export type ViolationsProps = {
  violations: readonly Violation[];
  /** The heading; by default it says the change breaks the rules. */
  title?: string;
};

/** The rules a change breaks as a submit or an approval would check them now. */
export function Violations({ violations, title }: ViolationsProps) {
  return (
    <Card role="alert" className="border-error/40 bg-error-light gap-3">
      <CardHeader>
        <CardTitle className="text-base">
          {title ?? `The change breaks ${violations.length === 1 ? "a rule" : "rules"}`}
        </CardTitle>
      </CardHeader>
      <CardContent>
        <ul className="flex flex-col gap-1 text-sm">
          {violations.map((violation: Violation, index: number) => (
            <li key={`${violation.path ?? ""}:${violation.rule}:${index}`} className="break-words">
              {violation.path && <code className="font-mono">{violation.path}</code>}
              {violation.path && ": "}
              {violation.message ?? violation.rule}
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  );
}
