"use client";

import { useState, type FormEvent } from "react";
import { Plus } from "lucide-react";

import { Button } from "@oba/ui/sky/button";
import { useMembers } from "@/features/members/hooks/use-members";
import type { MemberResponse } from "@/features/members/schemas";
import { firstIssue } from "@/lib/forms";
import { describeError } from "@/lib/api/error-text";
import { useIdentity } from "@/lib/identity/identity-context";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@oba/ui/sky/dialog";
import { Field, Input, NativeSelect } from "@oba/ui/sky/field";

import { useCreateMachine } from "../hooks/use-machines";
import { machineCreateRequestSchema } from "../schemas";
import { TokenReveal } from "./token-reveal";

/** The select value for "the signed-in user". */
const SELF = "";

/** The members a machine may be registered for: active ones with Kaya access, other than me. */
export function machineOwnerOptions(
  members: readonly MemberResponse[],
  myUserId: string
): MemberResponse[] {
  return members.filter(
    (member: MemberResponse) =>
      member.status === "ACTIVE" && member.role !== "GUEST" && member.userId !== myUserId
  );
}

/** The user select an admin gets; "Me" is the signed-in user. */
function UserSelect({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  const { me } = useIdentity();
  const members = useMembers();
  const options = machineOwnerOptions(members.data?.data ?? [], me.id);
  return (
    <Field
      label="User"
      error={
        members.error === null
          ? null
          : `The members could not be loaded: ${describeError(members.error)}`
      }
      hint="An admin may register a machine for another member of the organization."
    >
      <NativeSelect value={value} onChange={(event) => onChange(event.target.value)}>
        <option value={SELF}>Me</option>
        {options.map((member: MemberResponse) => (
          <option key={member.userId} value={member.userId}>
            {`${member.displayName} (${member.email})`}
          </option>
        ))}
      </NativeSelect>
    </Field>
  );
}

/**
 * Registers a machine and shows its recorder token once. A
 * member registers their own machines; an admin may pick another user.
 */
export function NewMachineDialog() {
  const { can, me } = useIdentity();
  const create = useCreateMachine();
  const [open, setOpen] = useState(false);
  const [machineName, setMachineName] = useState("");
  const [user, setUser] = useState(SELF);
  const [error, setError] = useState<string | null>(null);
  const [created, setCreated] = useState<{ token: string; machineName: string } | null>(null);
  const isAdmin = can("ADMIN");
  const forOther = user !== SELF && user !== me.id;

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const parsed = machineCreateRequestSchema.safeParse({
      machineName,
      userId: forOther ? user : undefined,
    });
    if (!parsed.success) {
      setError(firstIssue(parsed.error));
      return;
    }
    setError(null);
    create.mutate(parsed.data, {
      onSuccess: (response) => {
        const data = response.data;
        if (data === null || data === undefined) {
          setError(
            "Kaya registered the machine but sent no token. Rotate the token on the machine's page."
          );
          return;
        }
        setOpen(false);
        setMachineName("");
        setCreated({ token: data.token, machineName: data.machine.machineName });
      },
      onError: (failure: unknown) => setError(describeError(failure)),
    });
  };

  return (
    <>
      <Dialog
        open={open}
        onOpenChange={(next: boolean) => {
          setOpen(next);
          if (!next) setError(null);
        }}
      >
        <DialogTrigger asChild>
          <Button>
            <Plus className="size-4" aria-hidden />
            New machine
          </Button>
        </DialogTrigger>
        <DialogContent>
          <form onSubmit={submit} className="flex flex-col gap-4">
            <DialogHeader>
              <DialogTitle>New machine</DialogTitle>
              <DialogDescription>
                A machine is one Claude Code installation on one computer. Use the computer&apos;s
                host name (`hostname`), which the recorder reports with every batch.
              </DialogDescription>
            </DialogHeader>
            <Field label="Machine name">
              <Input
                value={machineName}
                maxLength={255}
                autoFocus
                placeholder="ada-macbook"
                onChange={(event) => setMachineName(event.target.value)}
              />
            </Field>
            {isAdmin && <UserSelect value={user} onChange={setUser} />}
            {error !== null && (
              <p className="text-error text-sm" role="alert">
                {error}
              </p>
            )}
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setOpen(false)}
                disabled={create.isPending}
              >
                Cancel
              </Button>
              <Button type="submit" disabled={create.isPending}>
                Register
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
      <TokenReveal
        token={created?.token ?? null}
        machineName={created?.machineName ?? ""}
        onClose={() => setCreated(null)}
      />
    </>
  );
}
