import { PATH_MAPPING_VERSIONS_MAX } from "../hooks/use-path-mappings";

export type ScopeCountNotesProps = {
  scope: string | null;
  /** The number of versions the scope holds, to say why counts are missing above the limit. */
  versionCount: number;
  /** What a chosen scope adds, such as "count its files". */
  withScope: string;
};

/** Why the counts or states are missing: no scope chosen, or more versions than Kaya counts at once. */
export function ScopeCountNotes({ scope, versionCount, withScope }: ScopeCountNotesProps) {
  return (
    <>
      {scope === null && (
        <p className="text-muted-foreground text-sm">
          Choose a repository or pack to {withScope}.
        </p>
      )}
      {versionCount > PATH_MAPPING_VERSIONS_MAX && (
        <p className="text-muted-foreground text-sm">
          This scope takes its files from more than {PATH_MAPPING_VERSIONS_MAX} versions, more than
          Kaya counts at once, so the table shows the paths only.
        </p>
      )}
    </>
  );
}
