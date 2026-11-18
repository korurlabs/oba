"use client";

import { useState, type FormEvent, type ReactNode } from "react";

import { Button } from "@oba/ui/sky/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@oba/ui/sky/dialog";
import { Field, Input, NativeSelect, Textarea } from "@oba/ui/sky/field";
import { describeError } from "@/lib/api/error-text";

import { useCreatePack } from "../hooks/use-owners";
import { PACK_LIMITS, packCreateRequestSchema, type Owner } from "../schemas";
import { ownerById } from "./owners";
import type { Scope } from "./scope-data";

function byName(left: Owner, right: Owner): number {
  return left.name.localeCompare(right.name);
}

/**
 * An owner creates a root pack, or a child pack of the parent chosen here,
 * which starts at `defaultParentId`. The parent is fixed at creation and
 * cannot be changed later, so no loop can form. The new pack
 * starts empty, with no version: nothing is copied from the parent, whose
 * items it inherits. The server refuses a name the organization already
 * uses (409 KAY_CFG_008) and a parent that no longer exists (404); the
 * dialog shows the refusal.
 */
export function NewPackDialog({
  owners,
  defaultParentId,
  trigger,
  onCreated,
}: {
  /** Every pack of the organization, the parents to choose from. */
  owners: readonly Owner[];
  /** The parent chosen when the dialog opens, usually the selected pack; null for a root pack. */
  defaultParentId: string | null;
  trigger: ReactNode;
  onCreated: (scope: Scope) => void;
}) {
  const create = useCreatePack();
  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [parentId, setParentId] = useState<string | null>(defaultParentId);
  const [error, setError] = useState<string | null>(null);
  const parent = parentId === null ? null : (ownerById(owners, parentId) ?? null);

  const reset = () => {
    setName("");
    setParentId(
      defaultParentId !== null && ownerById(owners, defaultParentId) ? defaultParentId : null
    );
    setDescription("");
    setError(null);
  };

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const parsed = packCreateRequestSchema.safeParse(
      parent ? { name, description, parentPackId: parent.id } : { name, description }
    );
    if (!parsed.success) {
      setError(parsed.error.issues[0]?.message ?? "Check the values entered.");
      return;
    }
    setError(null);
    create.mutate(parsed.data, {
      onSuccess: (response) => {
        setOpen(false);
        reset();
        const pack = response.data;
        if (pack) onCreated({ kind: "PACK", packId: pack.id });
      },
      onError: (failure: unknown) => setError(describeError(failure)),
    });
  };

  return (
    <Dialog
      open={open}
      onOpenChange={(next: boolean) => {
        setOpen(next);
        if (next) reset();
      }}
    >
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent>
        <form onSubmit={submit} className="flex flex-col gap-4">
          <DialogHeader>
            <DialogTitle>New pack</DialogTitle>
            <DialogDescription>
              {parent
                ? `The child pack inherits every item of ${parent.name} and its parents. It starts empty and stores only the items it adds or overrides. Its parent cannot be changed later.`
                : "A root pack has no parent. It starts empty; a repository that uses it, or one of its child packs, receives its items."}
            </DialogDescription>
          </DialogHeader>
          <Field label="Parent">
            <NativeSelect
              value={parent?.id ?? ""}
              className="font-mono"
              onChange={(event) => setParentId(event.target.value || null)}
            >
              <option value="">None</option>
              {[...owners].sort(byName).map((owner: Owner) => (
                <option key={owner.id} value={owner.id}>
                  {owner.name}
                </option>
              ))}
            </NativeSelect>
          </Field>
          <Field
            label="Name"
            hint="Lowercase letters, digits and hyphens, unique in the organization."
          >
            <Input
              value={name}
              maxLength={PACK_LIMITS.nameMax}
              autoFocus
              className="font-mono"
              onChange={(event) => setName(event.target.value)}
            />
          </Field>
          <Field label="Description">
            <Textarea
              value={description}
              maxLength={PACK_LIMITS.descriptionMax}
              rows={3}
              onChange={(event) => setDescription(event.target.value)}
            />
          </Field>
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
              Create
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
