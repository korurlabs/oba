"use client";

import { Tabs, TabsContent, TabsList, TabsTrigger } from "@oba/ui/sky/tabs";
import type { FileHistoryResponse } from "@/features/changes/schemas";
import type { ScopeFile } from "@/features/scope/components/scope-files";

import { useEditorParams } from "../hooks/use-editor-params";
import { ChecksPanel } from "./checks-panel";
import { DependencyPanel } from "./dependency-panel";
import { DEFAULT_PANEL, PANELS, panelOf } from "./document-panels-data";
import { HistoryPanel } from "./history-panel";

export type DocumentPanelsProps = {
  file: ScopeFile;
  files: readonly ScopeFile[];
  /** The viewer's draft of the file's owner; null when none is open. */
  draftId: string | null;
  memberNames: ReadonlyMap<string, string>;
  onChoose: (entry: FileHistoryResponse) => void;
};

/**
 * The document's side panels, the open one kept in the URL as `panel`: what
 * the file links to and what uses it, who proposed and reviewed each of its
 * versions, and the checks of the viewer's draft. Only the open panel reads.
 */
export function DocumentPanels({
  file,
  files,
  draftId,
  memberNames,
  onChoose,
}: DocumentPanelsProps) {
  const [{ panel }, setEditor] = useEditorParams();
  const open = panel ?? DEFAULT_PANEL;
  return (
    <aside className="flex min-w-0 flex-col" aria-label="Document panels">
      <Tabs
        value={open}
        onValueChange={(value: string) => {
          const next = panelOf(value);
          if (next !== null) void setEditor({ panel: next === DEFAULT_PANEL ? null : next });
        }}
      >
        <TabsList>
          {PANELS.map((entry) => (
            <TabsTrigger key={entry.id} value={entry.id}>
              {entry.label}
            </TabsTrigger>
          ))}
        </TabsList>
        <TabsContent value="links">
          {open === "links" && <DependencyPanel file={file} files={files} />}
        </TabsContent>
        <TabsContent value="history">
          {open === "history" && (
            <HistoryPanel
              versionId={file.versionId}
              path={file.path}
              memberNames={memberNames}
              onChoose={onChoose}
            />
          )}
        </TabsContent>
        <TabsContent value="checks">
          {open === "checks" && <ChecksPanel draftId={draftId} />}
        </TabsContent>
      </Tabs>
    </aside>
  );
}
