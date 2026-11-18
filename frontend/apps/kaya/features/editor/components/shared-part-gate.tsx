"use client";

import { useEffect, useState, type ReactNode } from "react";

import { Button } from "@oba/ui/sky/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@oba/ui/sky/dialog";
import { dependencyVersionIds, useDependencies } from "@/features/links/hooks/use-dependencies";
import type { ScopeFile } from "@/features/scope/components/scope-files";
import type { BeforeSave } from "@/features/versions/components/file-editor";

import { isSkillPart, partGate, sharedPartWarning } from "./dependency-data";

export type SharedPartGateProps = {
  file: ScopeFile;
  files: readonly ScopeFile[];
  /** The editor, given the question each save asks; undefined when the file is no shared part. */
  children: (onBeforeSave: BeforeSave | undefined) => ReactNode;
};

/**
 * Asks before each save of a part that several skills share: the skills
 * render the same stored part, so a save made for one changes every one of
 * them. The part stays editable, and the warning shows above the editor.
 */
export function SharedPartGate({ file, files, children }: SharedPartGateProps) {
  const part = isSkillPart(file.path);
  const dependencies = useDependencies(part ? dependencyVersionIds(files, file) : [], file.path);
  const warning = sharedPartWarning(
    partGate({
      path: file.path,
      dependencies: dependencies.data?.data ?? undefined,
      failed: dependencies.error !== null,
    })
  );
  // The answer the open question waits for; null while no save asks.
  const [pending, setPending] = useState<((yes: boolean) => void) | null>(null);
  // A question replaced by a newer save, or left open when the editor goes
  // away, answers no, so its save never runs later. An answered question
  // ignores this second answer.
  useEffect(() => {
    if (pending === null) return;
    return () => pending(false);
  }, [pending]);

  const settle = (yes: boolean) => {
    pending?.(yes);
    setPending(null);
  };

  const ask: BeforeSave | undefined =
    warning === null
      ? undefined
      : () =>
          new Promise<boolean>((resolve: (yes: boolean) => void) => {
            setPending(() => resolve);
          });

  // The editor keeps the same wrapper and the same child slot whatever the
  // sharers turn out to be: a part found to be in one skill only, after the
  // read, must not remount the editor and drop what was typed meanwhile.
  return (
    <div className="flex min-w-0 flex-col gap-3">
      {warning !== null && (
        <p className="text-warning-text text-sm" role="note">
          {warning}
        </p>
      )}
      {children(ask)}
      {warning !== null && (
        <Dialog
          open={pending !== null}
          onOpenChange={(open: boolean) => {
            if (!open) settle(false);
          }}
        >
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Save a shared part</DialogTitle>
              <DialogDescription>{warning}</DialogDescription>
            </DialogHeader>
            <DialogFooter>
              <Button variant="outline" onClick={() => settle(false)}>
                Cancel
              </Button>
              <Button onClick={() => settle(true)}>Save for all of them</Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      )}
    </div>
  );
}
