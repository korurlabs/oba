"use client";

import { createSerializer, parseAsString, useQueryStates } from "nuqs";
import type { Values } from "nuqs";

/**
 * URL state of the chat view, so an open thread, a jump target and a search
 * survive a reload and can be shared as a link. The open channel is the
 * path, not a parameter (`features/chat/lib/chat-route.ts`): switching
 * channels navigates to another path, which pushes a history entry, so Back
 * returns to the previous channel.
 *
 * - `thread`: the thread open in the side panel. Opening it pushes a history
 *   entry, so Back closes the panel (the full-screen sheet on a phone).
 * - `jump`: a message to show, loaded around it (`around`) and flashed.
 * - `threadMessage`: a message of the open thread to show in its panel, as
 *   `jump` does in the channel; read once and then dropped from the URL.
 * - `q`: the search text (at least 2 characters are searched; debounced by
 *   `SenseySearchInput`). `author`: the search's author filter (user ID).
 */
export const chatParams = {
  thread: parseAsString.withOptions({ history: "push" }),
  jump: parseAsString,
  threadMessage: parseAsString,
  q: parseAsString,
  author: parseAsString,
};

export type ChatParams = Values<typeof chatParams>;

/** Builds a link into the chat view, e.g. the link to a message. */
export const serializeChatParams = createSerializer(chatParams);

export function useChatParams() {
  return useQueryStates(chatParams);
}
