import type { SelectOption } from "@oba/shared/features/form";
import { memberName } from "@/features/members/lib/member-options";
import type { RelationOwner, RelationOwnerCandidate } from "../schemas";

/** Only `true` grants access: a candidate without the field cannot be a new owner. */
export function hasOtakAccess(candidate: RelationOwnerCandidate): boolean {
  return candidate.otakAccess === true;
}

/**
 * The owner select's options, in the server's order. A member without Otak
 * access is listed but cannot be picked, with `noAccessLabel` as the reason.
 * The stored owner stays pickable and is added when no longer listed: the
 * server checks access only for a new owner, so keeping them never fails.
 */
export function ownerCandidateOptions(
  candidates: readonly RelationOwnerCandidate[] | undefined,
  { currentOwner, noAccessLabel }: { currentOwner?: RelationOwner | null; noAccessLabel: string }
): SelectOption[] {
  const options: SelectOption[] = (candidates ?? []).map((candidate) => {
    const pickable = hasOtakAccess(candidate) || candidate.userId === currentOwner?.id;
    return pickable
      ? { value: candidate.userId, label: memberName(candidate) }
      : {
          value: candidate.userId,
          label: memberName(candidate),
          disabled: true,
          disabledLabel: noAccessLabel,
        };
  });
  if (currentOwner && !options.some((option) => option.value === currentOwner.id)) {
    options.push({ value: currentOwner.id, label: currentOwner.name });
  }
  return options;
}
