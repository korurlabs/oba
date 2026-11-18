import Link from "next/link";

import type { PlanReferenceResponse } from "../schemas";
import { packHref, referenceName } from "./plan-data";

/** Why a spec has no pack: Kaya matches the folder after docs/claude/plans/ to a pack by name. */
export const NO_PACK_TITLE =
  "No pack the repository uses, nor a parent of one, is named after the folder of this spec.";

/**
 * A plan's pack, linked to its page; "None" when no pack matched, with the
 * reason on a spec, whose folder names the pack.
 */
export function PlanPack({
  pack,
  spec,
}: {
  pack: PlanReferenceResponse | null | undefined;
  spec: boolean;
}) {
  if (!pack) {
    return (
      <span className="text-muted-foreground" title={spec ? NO_PACK_TITLE : undefined}>
        None
      </span>
    );
  }
  return (
    <Link href={packHref(pack)} className="hover:underline">
      {referenceName(pack)}
    </Link>
  );
}
