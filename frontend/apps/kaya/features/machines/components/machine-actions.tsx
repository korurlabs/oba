"use client";

import { useState } from "react";
import { Ban, KeyRound, RotateCw } from "lucide-react";

import { Button } from "@oba/ui/sky/button";
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

/** The machine's token and retirement buttons, each shown only to who may use it. */
export function MachineActions({
  machine,
  size = "sm",
}: {
  machine: MachineResponse;
  size?: "sm" | "xs";
}) {
  const { me, can } = useIdentity();
  const rotate = useRotateMachineToken();
  const revoke = useRevokeMachineToken();
  const retire = useRetireMachine();
  const [token, setToken] = useState<string | null>(null);
  const allowed = machinePermissions(machine, me.id, can("ADMIN"));

  if (!allowed.rotate && !allowed.revoke && !allowed.retire) return null;

  return (
    <div className="flex flex-wrap gap-2">
      {allowed.rotate && (
        <ConfirmDialog
          trigger={
            <Button variant="outline" size={size}>
              <RotateCw aria-hidden />
              {machine.token ? "Rotate token" : "New token"}
            </Button>
          }
          title={`New token for ${machine.machineName}`}
          description={
            machine.token
              ? `The current token (${machine.token.tokenPrefix}…) stops working at once. The recorder on ${machine.machineName} sends nothing until you store the new token there.`
              : `${machine.machineName} has no live token. A new one lets its recorder send again.`
          }
          confirmLabel={machine.token ? "Rotate" : "Create token"}
          onConfirm={async () => {
            const response = await rotate.mutateAsync(machine.id);
            const plaintext = response.data?.token;
            if (plaintext === undefined) {
              throw new Error("Kaya issued the token but sent no value. Rotate again.");
            }
            setToken(plaintext);
          }}
        />
      )}
      {allowed.revoke && (
        <ConfirmDialog
          trigger={
            <Button variant="outline" size={size}>
              <KeyRound aria-hidden />
              Revoke token
            </Button>
          }
          title={`Revoke the token of ${machine.machineName}`}
          description={`The token ${machine.token?.tokenPrefix ?? ""}… stops working at once and ${machine.machineName} records nothing more until its user creates a new one. The machine stays active.`}
          confirmLabel="Revoke"
          destructive
          onConfirm={() => revoke.mutateAsync(machine.id)}
        />
      )}
      {allowed.retire && (
        <ConfirmDialog
          trigger={
            <Button variant="outline" size={size}>
              <Ban aria-hidden />
              Retire
            </Button>
          }
          title={`Retire ${machine.machineName}`}
          description={`${machine.machineName} is retired and its token revoked. Its sessions stay. Registering the same machine name again brings it back with a new token.`}
          confirmLabel="Retire"
          destructive
          onConfirm={() => retire.mutateAsync(machine.id)}
        />
      )}
      <TokenReveal token={token} machineName={machine.machineName} onClose={() => setToken(null)} />
    </div>
  );
}
