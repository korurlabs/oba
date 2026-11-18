import { useEffect, useRef, useState } from "react";
import type { JSONContent } from "@tiptap/core";

/** What a thread's composer had not sent yet: its text as editor content, and its files. */
export interface ChatThreadDraft {
  /** Null when only files were added. */
  content: JSONContent | null;
  files: File[];
}

/**
 * Unsent drafts by thread ID, in memory for this tab. A thread's composer is
 * removed while the thread is closed or not open, and what was typed into it
 * comes back with the composer.
 */
const threadDrafts = new Map<string, ChatThreadDraft>();

/**
 * Restores and keeps the unsent draft of a thread's composer; the channel
 * (`threadId` null) keeps none. Returns the draft to start from.
 *
 * When the composer goes, `readDraft` says what to keep: a draft, null for an
 * empty composer (which forgets the kept draft), or undefined when the editor
 * never became ready, which leaves the kept draft as it is.
 */
export function useChatThreadDraft(
  threadId: string | null,
  readDraft: () => ChatThreadDraft | null | undefined,
): ChatThreadDraft | null {
  const [initial] = useState(() => (threadId ? (threadDrafts.get(threadId) ?? null) : null));

  const readDraftRef = useRef(readDraft);
  useEffect(() => {
    readDraftRef.current = readDraft;
  }, [readDraft]);

  useEffect(() => {
    if (!threadId) return undefined;
    return () => {
      const draft = readDraftRef.current();
      if (draft === undefined) return;
      if (draft) threadDrafts.set(threadId, draft);
      else threadDrafts.delete(threadId);
    };
  }, [threadId]);

  return initial;
}
