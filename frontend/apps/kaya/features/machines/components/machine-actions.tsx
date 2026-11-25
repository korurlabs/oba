"use client";

import { useRef, useState } from "react";
import { Ban, KeyRound, MoreVertical, RotateCw, type LucideIcon } from "lucide-react";

import { Button } from "@oba/ui/sky/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@oba/ui/sky/dropdown-menu";
import { useIdentity } from "@/lib/identity/identity-context";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";

import {
  useRetireMachine,
  useRevokeMachineToken,
  useRotateMachineToken,
} from "../hooks/use-machines";
import type { MachineResponse } from "../schemas";
import { machinePermissions } from "./machine-text";
import { TokenReveal } from "./token-reveal";

type MachineActionKind = "rotate" | "revoke" | "retire";

/** One action on a machine: how it is offered and the confirmation it asks for. */
type MachineAction = {
  kind: MachineActionKind;
  label: string;
  icon: LucideIcon;
  /** Revoking and retiring stop the recorder; rotating hands it a new token. */
  destructive: boolean;
  title: string;
  description: string;
  confirmLabel: string;
  onConfirm: () => Promise<unknown>;
};

export type MachineActionsProps = {
  machine: MachineResponse;
  /**
   * "buttons" shows one button per action, as on the machine's page; "menu"
   * puts them behind one icon button, so a table row stays compact.
   */
  layout?: "buttons" | "menu";
};

/** The machine's token and retirement actions, each offered only to who may use it. */
export function MachineActions({ machine, layout = "buttons" }: MachineActionsProps) {
  const { me, can } = useIdentity();
  const rotate = useRotateMachineToken();
  const revoke = useRevokeMachineToken();
  const retire = useRetireMachine();
  const [token, setToken] = useState<string | null>(null);
  const [confirming, setConfirming] = useState<MachineActionKind | null>(null);
  const menuTrigger = useRef<HTMLButtonElement>(null);
  const allowed = machinePermissions(machine, me.id, can("ADMIN"));

  const actions: MachineAction[] = [];
  if (allowed.rotate) {
    actions.push({
      kind: "rotate",
      label: machine.token ? "Rotate token" : "New token",
      icon: RotateCw,
      destructive: false,
      title: `New token for ${machine.machineName}`,
      description: machine.token
        ? `The current token (${machine.token.tokenPrefix}…) stops working at once. The recorder on ${machine.machineName} sends nothing until you store the new token there.`
        : `${machine.machineName} has no live token. A new one lets its recorder send again.`,
      confirmLabel: machine.token ? "Rotate" : "Create token",
      onConfirm: async () => {
        const response = await rotate.mutateAsync(machine.id);
        const plaintext = response.data?.token;
        if (plaintext === undefined) {
          throw new Error("Kaya issued the token but sent no value. Rotate again.");
        }
        setToken(plaintext);
      },
    });
  }
  if (allowed.revoke) {
    actions.push({
      kind: "revoke",
      label: "Revoke token",
      icon: KeyRound,
      destructive: true,
      title: `Revoke the token of ${machine.machineName}`,
      description: `The token ${machine.token?.tokenPrefix ?? ""}… stops working at once and ${machine.machineName} records nothing more until its user creates a new one. The machine stays active.`,
      confirmLabel: "Revoke",
      onConfirm: () => revoke.mutateAsync(machine.id),
    });
  }
  if (allowed.retire) {
    actions.push({
      kind: "retire",
      label: "Retire",
      icon: Ban,
      destructive: true,
      title: `Retire ${machine.machineName}`,
      description: `${machine.machineName} is retired and its token revoked. Its sessions stay. Registering the same machine name again brings it back with a new token.`,
      confirmLabel: "Retire",
      onConfirm: () => retire.mutateAsync(machine.id),
    });
  }

  // A refresh can take away the action being confirmed, for example once the
  // token was revoked elsewhere. Its dialog then unmounts without closing, and
  // the kept choice would reopen it unasked once the action is offered again.
  if (confirming !== null && !actions.some((action: MachineAction) => action.kind === confirming)) {
    setConfirming(null);
  }

  if (actions.length === 0) return null;

  const reveal = (
    <TokenReveal token={token} machineName={machine.machineName} onClose={() => setToken(null)} />
  );

  if (layout === "buttons") {
    return (
      <div className="flex flex-wrap gap-2">
        {actions.map((action: MachineAction) => (
          <ConfirmDialog
            key={action.kind}
            trigger={
              <Button variant="outline" size="sm">
                <action.icon aria-hidden />
                {action.label}
              </Button>
            }
            title={action.title}
            description={action.description}
            confirmLabel={action.confirmLabel}
            destructive={action.destructive}
            onConfirm={action.onConfirm}
          />
        ))}
        {reveal}
      </div>
    );
  }

  const safe = actions.filter((action: MachineAction) => !action.destructive);
  const destructive = actions.filter((action: MachineAction) => action.destructive);
  const item = (action: MachineAction) => (
    <DropdownMenuItem
      key={action.kind}
      variant={action.destructive ? "destructive" : "default"}
      onSelect={() => setConfirming(action.kind)}
    >
      <action.icon aria-hidden />
      {action.label}
    </DropdownMenuItem>
  );

  // The confirmations sit beside the menu, not inside it: the menu unmounts
  // its items when it closes, which would take an open dialog with it.
  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button ref={menuTrigger} variant="ghost" size="icon-sm">
            <MoreVertical aria-hidden />
            <span className="sr-only">Actions for {machine.machineName}</span>
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          {safe.map(item)}
          {safe.length > 0 && destructive.length > 0 && <DropdownMenuSeparator />}
          {destructive.map(item)}
        </DropdownMenuContent>
      </DropdownMenu>
      {actions.map((action: MachineAction) => (
        <ConfirmDialog
          key={action.kind}
          open={confirming === action.kind}
          onOpenChange={(open: boolean) => {
            // An action that finishes after its dialog was dismissed closes only
            // its own confirmation, not one the user opened since.
            if (!open) {
              setConfirming((current: MachineActionKind | null) =>
                current === action.kind ? null : current
              );
            }
          }}
          onCloseAutoFocus={(event: Event) => {
            event.preventDefault();
            menuTrigger.current?.focus();
          }}
          title={action.title}
          description={action.description}
          confirmLabel={action.confirmLabel}
          destructive={action.destructive}
          onConfirm={action.onConfirm}
        />
      ))}
      {reveal}
    </>
  );
}
