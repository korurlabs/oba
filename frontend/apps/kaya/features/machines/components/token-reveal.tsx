"use client";

import { useState } from "react";
import { Check, Copy, KeyRound } from "lucide-react";

import { Button } from "@oba/ui/sky/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@oba/ui/sky/dialog";

import { tokenCommand } from "./machine-text";

function CopyBlock({ label, value }: { label: string; value: string }) {
  const [copied, setCopied] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      setError(null);
    } catch (failure: unknown) {
      console.warn("[machines] Copy to the clipboard failed", failure);
      setError("Copying failed. Select the text and copy it by hand.");
    }
  };
  return (
    <div className="flex flex-col gap-1.5">
      <span className="text-sm font-medium">{label}</span>
      <div className="flex items-start gap-2">
        <code className="bg-muted min-w-0 flex-1 rounded-md px-2 py-1.5 font-mono text-xs break-all select-all">
          {value}
        </code>
        <Button
          variant="outline"
          size="icon-sm"
          onClick={() => void copy()}
          aria-label={`Copy ${label.toLowerCase()}`}
        >
          {copied ? (
            <Check className="size-4" aria-hidden />
          ) : (
            <Copy className="size-4" aria-hidden />
          )}
        </Button>
      </div>
      {error !== null && (
        <span className="text-error text-xs" role="alert">
          {error}
        </span>
      )}
    </div>
  );
}

export type TokenRevealProps = {
  /** The plaintext token, or null while there is nothing to show. */
  token: string | null;
  machineName: string;
  onClose: () => void;
};

/**
 * Shows a new recorder token once. Kaya keeps only its hash, so no one,
 * admins included, can read it again; a lost token is replaced by a rotation.
 */
export function TokenReveal({ token, machineName, onClose }: TokenRevealProps) {
  return (
    <Dialog open={token !== null} onOpenChange={(open: boolean) => !open && onClose()}>
      <DialogContent onInteractOutside={(event) => event.preventDefault()}>
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <KeyRound className="size-5" aria-hidden />
            Machine token for {machineName}
          </DialogTitle>
          <DialogDescription>
            Copy it now: Kaya shows this token only once. On {machineName}, run the command in any
            directory to store it in your personal Kagi environment.
          </DialogDescription>
        </DialogHeader>
        {token !== null && (
          <>
            <CopyBlock label="Token" value={token} />
            <CopyBlock label="Command" value={tokenCommand(token)} />
          </>
        )}
        <DialogFooter>
          <Button onClick={onClose}>I stored the token</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
