import Link from "next/link";

import { InfoTip } from "@oba/ui/sky/info-tip";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@oba/ui/sky/table";
import type { SettingsHookResponse } from "../schemas";

export type HookListProps = {
  hooks: readonly SettingsHookResponse[];
  /** The Rules page on its Hooks tab for the same scope. */
  scriptsHref: string;
};

/** The hook commands `.claude/settings.json` registers, read-only; their scripts are under Rules. */
export function HookList({ hooks, scriptsHref }: HookListProps) {
  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center gap-1.5 text-sm">
        <Link href={scriptsHref} className="text-primary underline-offset-4 hover:underline">
          Hook scripts
        </Link>
        <InfoTip label="What do hooks do?">
          Hooks run before or after a tool call and can refuse it whatever the rules say. Their
          scripts are on the Rules page, Hooks tab.
        </InfoTip>
      </div>
      {hooks.length === 0 ? (
        <p className="text-muted-foreground text-sm">No hook is registered.</p>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Event</TableHead>
              <TableHead>Matcher</TableHead>
              <TableHead>Command</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {hooks.map((hook: SettingsHookResponse, index: number) => (
              <TableRow key={`${hook.event}:${hook.matcher ?? ""}:${index}`}>
                <TableCell className="font-medium">{hook.event}</TableCell>
                <TableCell className="font-mono text-xs">{hook.matcher ?? "Every tool"}</TableCell>
                <TableCell className="font-mono text-xs break-all whitespace-normal">
                  {hook.command ?? (hook.type ? `(${hook.type})` : "")}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </div>
  );
}
