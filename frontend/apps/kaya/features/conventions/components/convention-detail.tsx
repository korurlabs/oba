"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

import { ViewToggle } from "@/features/editor/components/view-toggle";
import { useEditorParams, type EditorView } from "@/features/editor/hooks/use-editor-params";
import {
  BackLink,
  DetailNav,
  FileDetail,
  useCurrentQuery,
  type Neighbour,
} from "@/features/rules/components/file-detail";
import { ScopePage, type ReadyScope } from "@/features/scope/components";
import { mountRefOf } from "@/features/scope/components/mount-paths";
import type { ScopeFile } from "@/features/scope/components/scope-files";
import { editTarget } from "@/features/scope/components/scope-data";
import { SubmittedNotice } from "@/features/versions/components/change-control";
import { FileEditor } from "@/features/versions/components/file-editor";
import { useEditSource, type SubmittedChange } from "@/features/versions/hooks/use-change-write";

import { useConventions } from "../hooks/use-conventions";
import {
  conventionFiles,
  conventionGroups,
  conventionTitles,
  conventionVersionIds,
  folderOf,
  mountNewFileFolder,
  newFileFolder,
  type ConventionGroup,
} from "./convention-files";
import { conventionDetailHref, conventionsListHref } from "./convention-links";
import { CONVENTIONS_DESCRIPTION } from "./conventions-page";

const BACK_LABEL = "Back to Conventions";

/** The doc before or after one, in list order, as an arrow's target. */
function neighbourOf(doc: ScopeFile | undefined): Neighbour | null {
  return doc === undefined
    ? null
    : {
        to: (current: URLSearchParams) => conventionDetailHref(doc.path, current),
        name: doc.path,
      };
}

/**
 * One delivered doc: the back link and the arrows over the doc's header,
 * read view and editor, its title as the description line.
 */
function ConventionDoc({
  file,
  docs,
  title,
  ready,
}: {
  file: ScopeFile;
  /** The docs in list order, for the arrows. */
  docs: readonly ScopeFile[];
  title: string | null;
  ready: ReadyScope;
}) {
  const router = useRouter();
  const current = useCurrentQuery();
  const index = docs.findIndex((doc: ScopeFile) => doc.path === file.path);
  const backToFolder = (query: URLSearchParams) => conventionsListHref(query, folderOf(file.path));

  return (
    <FileDetail
      file={file}
      ready={ready}
      summary={null}
      description={title}
      heading={<h1 className="min-w-0 font-mono text-base break-all">{file.path}</h1>}
      nav={(editing: boolean) => (
        <DetailNav
          back={backToFolder}
          backLabel={BACK_LABEL}
          previous={neighbourOf(docs[index - 1])}
          next={neighbourOf(docs[index + 1])}
          showSteps={!editing && index !== -1}
        />
      )}
      onRenamed={(path: string) => router.replace(conventionDetailHref(path, current))}
      onDeleted={() => router.push(backToFolder(current))}
    />
  );
}

/** The page of a doc the scope does not deliver, such as one removed or not inherited here. */
function NotDelivered({ path }: { path: string }) {
  return (
    <div className="flex flex-col gap-4">
      <BackLink to={conventionsListHref} label={BACK_LABEL} />
      <p className="text-muted-foreground text-sm">
        This scope does not deliver <code className="font-mono break-all">{path}</code>.
      </p>
    </div>
  );
}

/**
 * A new doc, starting in the folder the address names while the scope
 * delivers docs there, else at the docs root (in a mounted pack, in the
 * pack's docs folder). Saved into the open change, it gets its own page;
 * submitted as a one-edit change, the page says it is in review. Cancel
 * returns to the list.
 */
function NewConventionDoc({ ready }: { ready: ReadyScope }) {
  const router = useRouter();
  const current = useCurrentQuery();
  const [{ view }, setEditor] = useEditorParams();
  const source = useEditSource(ready);
  const [submitted, setSubmitted] = useState<SubmittedChange | null>(null);
  const target = editTarget(ready.scope, ready.owners);
  const docs = conventionFiles(ready.files);
  const folder = newFileFolder(current.get("folder"), null, docs);
  const named = current.get("folder") !== null;
  const backToFolder = (query: URLSearchParams) =>
    conventionsListHref(query, named ? folder : null);

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <BackLink to={backToFolder} label={BACK_LABEL} />
      <h1>New file</h1>
      {submitted !== null ? (
        <p className="text-sm">
          <SubmittedNotice submitted={submitted} />
        </p>
      ) : target === null ? (
        <p className="text-muted-foreground text-sm">Choose a pack to add a file.</p>
      ) : source === null ? null : (
        <div className="flex min-w-0 flex-col gap-3">
          <ViewToggle view={view} onChange={(next: EditorView) => void setEditor({ view: next })} />
          <FileEditor
            key="new"
            source={source}
            file={null}
            initialPath={mountNewFileFolder(folder, mountRefOf(target))}
            view={view}
            onSaved={(path: string) => router.replace(conventionDetailHref(path, current))}
            onSubmitted={setSubmitted}
            onCancel={() => router.push(backToFolder(current))}
          />
        </div>
      )}
    </div>
  );
}

/**
 * The doc a detail page names, found by its path among the delivered docs in
 * list order. The open change's docs and, with `inherited`, the inherited
 * docs are part of the scope, so a path only the change has, or an inherited
 * one, resolves too.
 */
function ConventionDetailView({ path, ready }: { path: string; ready: ReadyScope }) {
  const docs = conventionGroups(ready.files).flatMap((group: ConventionGroup) => group.docs);
  const conventions = useConventions(conventionVersionIds(docs));
  const rows = conventions.data?.data;
  const file = docs.find((entry: ScopeFile) => entry.path === path);
  if (file === undefined) return <NotDelivered path={path} />;
  const title = rows ? (conventionTitles(docs, rows).get(path) ?? null) : null;
  return <ConventionDoc key={file.path} file={file} docs={docs} title={title} ready={ready} />;
}

export type ConventionDetailProps = {
  /** The doc's repository path; null for a new doc. */
  path: string | null;
};

/** A convention doc's own page, or the page that starts a new doc, in the scope the URL names. */
export function ConventionDetail({ path }: ConventionDetailProps) {
  return (
    <ScopePage title="Conventions" description={CONVENTIONS_DESCRIPTION} variant="detail">
      {(ready: ReadyScope) =>
        path === null ? (
          <NewConventionDoc ready={ready} />
        ) : (
          <ConventionDetailView path={path} ready={ready} />
        )
      }
    </ScopePage>
  );
}
