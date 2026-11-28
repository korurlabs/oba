"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import type { ReactNode } from "react";
import { ArrowLeft } from "lucide-react";

import { Badge } from "@oba/ui/sky/badge";
import { Button } from "@oba/ui/sky/button";
import { PageHeading } from "@oba/ui/sky/page-heading";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { ReasonDialog } from "@/components/reason-dialog";
import { RebaseDialog } from "@/features/versions/components/change-control";
import { ChangeLockTip, useOpenChange } from "@/features/versions/components/open-change";
import { ownerById } from "@/features/scope/components/owners";
import { scopeOfOwner, scopeValue } from "@/features/scope/components/scope-data";
import { usePackEffective } from "@/features/scope/hooks/use-effective";
import { useOwners } from "@/features/scope/hooks/use-owners";
import type { EffectiveItemResponse, Owner } from "@/features/scope/schemas";
import { useMembers } from "@/features/members/hooks/use-members";
import { describeError } from "@/lib/api/error-text";
import { ERROR_CODES, KayaApiError } from "@/lib/api/errors";
import { dateTimeText } from "@/lib/format-time";
import { useIdentity } from "@/lib/identity/identity-context";
import { memberName } from "@/lib/identity/member-name";

import { useChange, useSubmitDraft } from "../hooks/use-changes";
import { useDeleteDraft, useRestoreVersion } from "@/features/versions/hooks/use-versions";
import type { ChangeDetailResponse, ReviewResponse } from "../schemas";
import {
  DECISION_LABELS,
  SOURCE_LABELS,
  STATUS_BADGES,
  approverText,
  changeTitle,
  draftViolationsTitle,
  versionOwnerLabel,
  mayRestore,
  reviewActions,
} from "./change-data";
import { DiffView } from "./diff-view";
import { CommentForm, ReviewButtons } from "./review-controls";
import { Violations } from "./violations";

function Meta({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-0.5">
      <dt className="text-muted-foreground text-xs">{label}</dt>
      <dd className="text-sm">{children}</dd>
    </div>
  );
}

function Reviews({
  reviews,
  memberNames,
}: {
  reviews: readonly ReviewResponse[];
  memberNames: ReadonlyMap<string, string>;
}) {
  if (reviews.length === 0) {
    return <p className="text-muted-foreground text-sm">No review or comment yet.</p>;
  }
  return (
    <ol className="flex flex-col gap-3">
      {reviews.map((review: ReviewResponse) => (
        <li key={review.id} className="flex flex-col gap-1 rounded-md border p-3">
          <div className="flex flex-wrap items-center gap-2 text-sm">
            <span className="font-medium">{memberName(review.reviewerId, memberNames)}</span>
            <span>{DECISION_LABELS[review.decision]}</span>
            <span className="text-muted-foreground text-xs">{dateTimeText(review.createdAt)}</span>
          </div>
          {review.comment && (
            <p className="text-sm break-words whitespace-pre-wrap">{review.comment}</p>
          )}
        </li>
      ))}
    </ol>
  );
}

/** An override whose base the change moves to a newer version of the parent's item. */
export type OverrideBaseMove = { path: string; packName: string; from: number; to: number };

/**
 * The overrides whose base differs between a change and the version it is
 * based on. A Keep moves only the base and changes no file, so without these
 * lines the reviewer would see a change with nothing in it.
 */
export function overrideBaseMoves(
  changed: readonly EffectiveItemResponse[],
  base: readonly EffectiveItemResponse[],
  owners: readonly Owner[] | undefined
): OverrideBaseMove[] {
  const before = new Map(
    base
      .filter((item: EffectiveItemResponse) => item.own && item.override?.baseVersionId)
      .map((item: EffectiveItemResponse) => [item.path, item.override] as const)
  );
  const moves: OverrideBaseMove[] = [];
  for (const item of changed) {
    const after = item.override;
    const previous = before.get(item.path);
    if (!item.own || !after?.baseVersionId || !previous?.baseVersionId) continue;
    if (after.baseVersionId === previous.baseVersionId) continue;
    if (after.baseItemVersion == null || previous.baseItemVersion == null) continue;
    const pack = owners?.find((owner: Owner) => owner.id === after.basePackId);
    moves.push({
      path: item.path,
      packName: pack?.name ?? "a parent pack",
      from: previous.baseItemVersion,
      to: after.baseItemVersion,
    });
  }
  return moves;
}

/** The override bases a change moves, listed above its file diffs. */
function OverrideBaseMoves({
  packId,
  versionId,
  baseVersionId,
  owners,
}: {
  packId: string;
  versionId: string;
  baseVersionId: string;
  owners: readonly Owner[] | undefined;
}) {
  const changed = usePackEffective(packId, versionId);
  const base = usePackEffective(packId, baseVersionId);
  const error = changed.error ?? base.error;
  if (error !== null) {
    return (
      <LoadError
        title="Could not load the overrides of this change"
        message={describeError(error)}
        onRetry={() => {
          void changed.refetch();
          void base.refetch();
        }}
      />
    );
  }
  const moves = overrideBaseMoves(changed.data?.data ?? [], base.data?.data ?? [], owners);
  if (moves.length === 0) return null;
  return (
    <ul className="flex flex-col gap-2" aria-label="Override bases">
      {moves.map((move: OverrideBaseMove) => (
        <li key={move.path} className="flex flex-col gap-0.5 rounded-md border p-3">
          <span className="text-sm font-medium">
            Override base v{move.from} to v{move.to}
          </span>
          <span className="text-muted-foreground text-xs">
            <span className="font-mono">{move.path}</span>, from {move.packName}
          </span>
        </li>
      ))}
    </ul>
  );
}

/**
 * Rebase, submit, delete and continue the viewer's own open change. Continue
 * editing makes it the open change, so it waits while another change is open:
 * only one change collects edits at a time.
 */
function DraftActions({
  change,
  lockedBy,
}: {
  change: ChangeDetailResponse;
  lockedBy: string | null;
}) {
  const router = useRouter();
  const submit = useSubmitDraft();
  const remove = useDeleteDraft();
  const { setChangeId } = useOpenChange();
  const { version } = change;
  const reviewed = version.decidedAt != null || change.reviews.length > 0;
  return (
    <div className="flex flex-wrap items-start gap-3">
      <span className="flex items-center gap-1">
        <Button
          size="sm"
          variant="outline"
          disabled={lockedBy !== null}
          onClick={() => {
            setChangeId(version.id);
            router.push(
              `/rules?scope=${encodeURIComponent(scopeValue(scopeOfOwner({ id: version.packId })))}`
            );
          }}
        >
          Continue editing
        </Button>
        {lockedBy !== null && <ChangeLockTip packName={lockedBy} />}
      </span>
      <ReasonDialog
        trigger={<Button size="sm">Submit for review</Button>}
        title="Submit this change for review"
        description={
          <p>
            {approverText(change.requiredRole)} other than you reviews it. Say why the change is
            needed; the reviewer reads it.
          </p>
        }
        confirmLabel="Submit"
        onConfirm={(reason: string) =>
          submit.mutateAsync({ draftId: version.id, data: { reason } })
        }
      />
      <RebaseDialog draftId={version.id} />
      {!reviewed && (
        <ConfirmDialog
          trigger={
            <Button size="sm" variant="outline">
              Delete change
            </Button>
          }
          title="Delete this change"
          description={<p>The change and its edited files are removed. This cannot be undone.</p>}
          confirmLabel="Delete"
          destructive
          onConfirm={async () => {
            await remove.mutateAsync(version.id);
            router.push("/changes");
          }}
        />
      )}
    </div>
  );
}

/**
 * One change: what it is, its diff against its base, the rules it breaks
 * now, its reviews, and what the viewer may do. The server's permissions
 * decide; a refused control is greyed out with the reason.
 */
export function ChangeDetail({ versionId }: { versionId: string }) {
  const identity = useIdentity();
  const router = useRouter();
  const change = useChange(versionId);
  const members = useMembers();
  const { owners } = useOwners();
  const restore = useRestoreVersion();
  const openChange = useOpenChange().change;
  const detail = change.data?.data;
  const memberNames = new Map(
    (members.data?.data ?? []).map((member) => [member.userId, member.displayName])
  );

  if (change.error !== null && detail === undefined) {
    const gone =
      change.error instanceof KayaApiError && change.error.code === ERROR_CODES.NOT_FOUND;
    return (
      <div className="flex flex-col gap-4">
        <BackLink />
        <LoadError
          title={gone ? "This change does not exist" : "Could not load the change"}
          message={describeError(change.error)}
          onRetry={() => void change.refetch()}
        />
      </div>
    );
  }
  if (!detail) return <TableLoading label="Loading the change" />;

  const { version } = detail;
  const status = STATUS_BADGES[version.status];
  const actions = reviewActions(detail, identity);
  const owner = owners?.find((entry: Owner) => entry.id === version.packId);
  const currentVersionId = owners ? (owner?.currentVersionId ?? null) : undefined;
  const ownerText = versionOwnerLabel(version, owners);
  const ownDraft = version.status === "DRAFT" && version.proposedBy === identity.me.id;
  const violations = detail.violations ?? [];
  const openPackName = (packId: string): string =>
    (owners && ownerById(owners, packId)?.name) ?? "another pack";
  const continueLockedBy =
    openChange !== null && openChange.id !== version.id ? openPackName(openChange.packId) : null;
  const restoreLockedBy =
    openChange !== null && openChange.packId !== version.packId
      ? openPackName(openChange.packId)
      : null;

  return (
    <div className="flex flex-col gap-6">
      <PageHeading
        title={changeTitle(version, owners)}
        back={{ href: "/changes", label: "All changes" }}
        description={
          <div className="mt-1 flex flex-col gap-2">
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant={status.variant}>{status.label}</Badge>
              {version.executableChange && <Badge variant="warning">Executable</Badge>}
              {version.id === currentVersionId && <Badge variant="info">Current</Badge>}
            </div>
            <dl className="grid grid-cols-2 gap-x-6 gap-y-2 sm:grid-cols-4">
              <Meta label="Of">{ownerText}</Meta>
              <Meta label="Source">{SOURCE_LABELS[version.source]}</Meta>
              <Meta label="Proposed by">{memberName(version.proposedBy, memberNames)}</Meta>
              <Meta label="Approval by">{approverText(detail.requiredRole)}</Meta>
              <Meta label="Created">{dateTimeText(version.createdAt)}</Meta>
              <Meta label="Submitted">
                {version.submittedAt ? dateTimeText(version.submittedAt) : "Not yet"}
              </Meta>
              {version.decidedAt && <Meta label="Decided">{dateTimeText(version.decidedAt)}</Meta>}
            </dl>
            {version.reason && (
              <p className="max-w-3xl text-sm break-words whitespace-pre-wrap">{version.reason}</p>
            )}
          </div>
        }
      />

      <div className="flex flex-wrap items-start gap-3">
        <ReviewButtons
          versionId={version.id}
          approve={actions.approve}
          reject={actions.reject}
          approveDescription={`Version ${version.number} becomes the current version of ${ownerText}, and every repository that composes it gets a new signed release.`}
        />
        {ownDraft && <DraftActions change={detail} lockedBy={continueLockedBy} />}
        {mayRestore(version, currentVersionId) && (
          <span className="flex items-center gap-1">
            <ConfirmDialog
              trigger={
                <Button size="sm" variant="outline" disabled={restoreLockedBy !== null}>
                  Restore
                </Button>
              }
              title={`Restore version ${version.number}`}
              description={
                <p>
                  A new change with the files of version {version.number} is created on top of the
                  current version. You submit it for review like any change.
                </p>
              }
              confirmLabel="Restore"
              onConfirm={async () => {
                const created = await restore.mutateAsync(version.id);
                const id = created.data?.id;
                if (!id)
                  throw new Error("Kaya restored the version but did not name the new change.");
                router.push(`/changes/${encodeURIComponent(id)}`);
              }}
            />
            {restoreLockedBy !== null && <ChangeLockTip packName={restoreLockedBy} />}
          </span>
        )}
      </div>

      {(version.status === "IN_REVIEW" || ownDraft) && violations.length > 0 && (
        <Violations
          violations={violations}
          title={ownDraft ? draftViolationsTitle(violations.length) : undefined}
        />
      )}

      <section className="flex flex-col gap-3" aria-labelledby="diff-title">
        <h2 id="diff-title">Files</h2>
        {version.baseVersionId && (
          <OverrideBaseMoves
            packId={version.packId}
            versionId={version.id}
            baseVersionId={version.baseVersionId}
            owners={owners}
          />
        )}
        <DiffView
          versionId={version.id}
          baseLabel={version.baseVersionId ? "the version it is based on" : "an empty version"}
        />
      </section>

      <section className="flex flex-col gap-3" aria-labelledby="reviews-title">
        <h2 id="reviews-title">Reviews and comments</h2>
        <Reviews reviews={detail.reviews} memberNames={memberNames} />
        <CommentForm versionId={version.id} state={actions.comment} />
      </section>
    </div>
  );
}

function BackLink() {
  return (
    <Link
      href="/changes"
      className="text-muted-foreground hover:text-foreground flex w-fit items-center gap-1 text-sm"
    >
      <ArrowLeft className="size-4" aria-hidden />
      All changes
    </Link>
  );
}
