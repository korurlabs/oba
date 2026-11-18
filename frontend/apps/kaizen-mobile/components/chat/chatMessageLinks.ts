import type { ChatInline } from "@/components/chat/chatMarkdown";
import { parseChatMessageLink, type ChatMessageLink } from "@/lib/kaizenLinks";

/** A paragraph or list item with its message links taken out, to show as previews below it. */
export interface ChatInlinesWithMessageLinks {
  inlines: ChatInline[];
  /** Each linked message once, in the order the text names them. */
  links: ChatMessageLink[];
}

/** True for a URL written as itself, not a markdown link with a label of its own. */
function isBareLink(inline: Extract<ChatInline, { type: "link" }>): boolean {
  const only: ChatInline | undefined = inline.children[0];
  return inline.children.length === 1 && only?.type === "text" && only.text === inline.url;
}

/**
 * Takes the bare links to chat messages out of inline text, as the web shows
 * them: a preview of the message in place of the URL. A link with a label
 * stays a link, since its writer chose the words the reader sees. Links
 * inside emphasis are taken out too.
 */
export function takeChatMessageLinks(inlines: readonly ChatInline[]): ChatInlinesWithMessageLinks {
  const links: ChatMessageLink[] = [];
  const seen = new Set<string>();
  const walk = (nodes: readonly ChatInline[]): ChatInline[] =>
    nodes.flatMap((node: ChatInline): ChatInline[] => {
      if (node.type === "bold" || node.type === "italic" || node.type === "strike") {
        return [{ ...node, children: walk(node.children) }];
      }
      if (node.type !== "link" || !isBareLink(node)) return [node];
      const link: ChatMessageLink | null = parseChatMessageLink(node.url);
      if (!link) return [node];
      if (!seen.has(link.messageId)) {
        seen.add(link.messageId);
        links.push(link);
      }
      return [];
    });
  const rest: ChatInline[] = walk(inlines);
  return { inlines: links.length > 0 ? rest : [...inlines], links };
}

/** True when inline text shows nothing but white space, as a paragraph that held only a link does. */
export function isBlankChatInlines(inlines: readonly ChatInline[]): boolean {
  return inlines.every((node: ChatInline): boolean => {
    if (node.type === "text") return node.text.trim().length === 0;
    if (node.type === "bold" || node.type === "italic" || node.type === "strike") {
      return isBlankChatInlines(node.children);
    }
    return false;
  });
}
