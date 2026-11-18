"use client";

import { useState, type FormEvent } from "react";
import { Pencil } from "lucide-react";

import { Badge } from "@oba/ui/sky/badge";
import { Button } from "@oba/ui/sky/button";
import { Input, Textarea } from "@oba/ui/sky/field";
import { InfoTip } from "@oba/ui/sky/info-tip";
import {
  PACK_LIMITS,
  packUpdateRequestSchema,
  type PackDetailResponse,
} from "@/features/scope/schemas";
import { useIdentity } from "@/lib/identity/identity-context";

import { useUpdatePack } from "../hooks/use-packs";
import { renameFailure, type RenameFailure } from "./pack-data";

/**
 * The pack's name and description, which an owner edits together. The name
 * may change only while nothing uses the pack; the server decides that, so a
 * refusal is shown under the form with the reason at hand. The description
 * may always change. The update replaces both, so the form always sends the
 * description it shows.
 */
export function PackName({ pack }: { pack: PackDetailResponse }) {
  const { can } = useIdentity();
  const update = useUpdatePack();
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(pack.name);
  const [description, setDescription] = useState(pack.description ?? "");
  const [failure, setFailure] = useState<RenameFailure | null>(null);

  const start = () => {
    setName(pack.name);
    setDescription(pack.description ?? "");
    setFailure(null);
    setEditing(true);
  };

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const parsed = packUpdateRequestSchema.safeParse({ name, description });
    if (!parsed.success) {
      setFailure({
        message: parsed.error.issues[0]?.message ?? "Check the values entered.",
        fixed: false,
      });
      return;
    }
    if (parsed.data.name === pack.name && parsed.data.description === (pack.description ?? null)) {
      setEditing(false);
      return;
    }
    setFailure(null);
    update.mutate(
      { packId: pack.id, data: parsed.data },
      {
        onSuccess: () => setEditing(false),
        onError: (error: unknown) => setFailure(renameFailure(error)),
      }
    );
  };

  return (
    <div className="flex flex-col gap-1">
      {editing ? (
        <form onSubmit={submit} className="flex max-w-2xl flex-col gap-2">
          <Input
            aria-label="Pack name"
            value={name}
            maxLength={PACK_LIMITS.nameMax}
            autoFocus
            className="w-64 font-mono"
            disabled={update.isPending}
            aria-invalid={failure !== null ? true : undefined}
            onChange={(event) => setName(event.target.value)}
          />
          <Textarea
            aria-label="Pack description"
            placeholder="Description"
            value={description}
            maxLength={PACK_LIMITS.descriptionMax}
            rows={3}
            disabled={update.isPending}
            onChange={(event) => setDescription(event.target.value)}
          />
          <div className="flex items-center gap-2">
            <Button type="submit" size="sm" disabled={update.isPending}>
              Save
            </Button>
            <Button
              type="button"
              size="sm"
              variant="outline"
              disabled={update.isPending}
              onClick={() => {
                setEditing(false);
                setFailure(null);
              }}
            >
              Cancel
            </Button>
          </div>
        </form>
      ) : (
        <>
          <div className="flex items-center gap-2">
            <h2 className="font-mono text-xl font-semibold">{pack.name}</h2>
            {pack.required && <Badge variant="secondary">Required</Badge>}
            {can("OWNER") && (
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label={`Edit ${pack.name}`}
                title="Edit"
                onClick={start}
              >
                <Pencil aria-hidden />
              </Button>
            )}
          </div>
          {pack.description && (
            <p className="text-muted-foreground max-w-2xl text-sm whitespace-pre-line">
              {pack.description}
            </p>
          )}
        </>
      )}
      {failure !== null && (
        <div className="flex items-center gap-1.5">
          <p className="text-error text-sm" role="alert">
            {failure.message}
          </p>
          {failure.fixed && (
            <InfoTip label="Why can the name not change?">
              A pack&apos;s name is fixed once it is used.
            </InfoTip>
          )}
        </div>
      )}
    </div>
  );
}
