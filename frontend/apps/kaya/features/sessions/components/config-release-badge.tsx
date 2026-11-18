import Link from "next/link";
import { FileCog } from "lucide-react";

import { Badge } from "@oba/ui/sky/badge";

import type { SessionDetailResponse } from "../schemas";
import { configReleaseView } from "./config-release";

/**
 * The config release the session ran under, its state and how it was
 * delivered; the release links to the paths it delivered.
 */
export function ConfigReleaseBadge({ detail }: { detail: SessionDetailResponse }) {
  const view = configReleaseView(detail);
  const release = (
    <Badge variant="outline" className="font-normal">
      <FileCog aria-hidden />
      {view.label}
    </Badge>
  );
  return (
    <span className="inline-flex flex-wrap items-center gap-1.5" aria-label="Config release">
      {view.href === null ? (
        release
      ) : (
        <Link href={view.href} title="The paths this release delivered">
          {release}
        </Link>
      )}
      {view.state !== null && (
        <Badge variant={view.state.variant} title={view.state.description}>
          {view.state.text}
          <span className="sr-only">: {view.state.description}</span>
        </Badge>
      )}
      {view.deliveredBy !== null && (
        <span className="text-muted-foreground text-xs">{view.deliveredBy}</span>
      )}
    </span>
  );
}
