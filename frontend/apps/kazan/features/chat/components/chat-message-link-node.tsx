"use client";

import { Node, type Editor } from "@tiptap/core";
import type { Node as ProseMirrorNode } from "@tiptap/pm/model";
import { Plugin, PluginKey, type EditorState, type Transaction } from "@tiptap/pm/state";
import { NodeViewWrapper, ReactNodeViewRenderer, type ReactNodeViewProps } from "@tiptap/react";
import { findChatMessageLinks, parseChatMessageLink } from "../lib/chat-message-link";
import { ChatMessageLinkPreview } from "./chat-message-link-chip";

/** Set on a transaction whose message links should become chips, as a paste does. */
export const CHAT_CONVERT_MESSAGE_LINKS_META = "chatConvertMessageLinks";

/** The chip of a message link in the composer: the preview, without the jump. */
function ChatMessageLinkNodeView({ node }: ReactNodeViewProps) {
  const link = parseChatMessageLink(String(node.attrs.href ?? ""));
  return (
    <NodeViewWrapper as="span" className="inline">
      {link ? <ChatMessageLinkPreview link={link} /> : String(node.attrs.href ?? "")}
    </NodeViewWrapper>
  );
}

/**
 * Replaces every chat message link written in the text of `tr.doc` with a
 * chip, except inside code, where a link stays literal. Returns true when
 * something was replaced.
 */
export function convertChatMessageLinks(tr: Transaction): boolean {
  const chipType = tr.doc.type.schema.nodes[ChatMessageLinkNode.name];
  if (!chipType) return false;
  const replacements: { from: number; to: number; node: ProseMirrorNode }[] = [];
  tr.doc.descendants((node, pos, parent) => {
    if (parent?.type.spec.code) return false;
    if (!node.isText || node.marks.some((mark) => mark.type.spec.code)) return true;
    for (const { from, to, link } of findChatMessageLinks(node.text ?? "")) {
      replacements.push({ from: pos + from, to: pos + to, node: chipType.create({ href: link.href }) });
    }
    return true;
  });
  // From the end, so earlier positions stay valid.
  replacements.sort((a, b) => b.from - a.from);
  for (const replacement of replacements) tr.replaceWith(replacement.from, replacement.to, replacement.node);
  return replacements.length > 0;
}

/** Turns the message links of the editor into chips, outside the undo history (a loaded message). */
export function convertEditorChatMessageLinks(editor: Editor): void {
  const tr = editor.state.tr;
  if (!convertChatMessageLinks(tr)) return;
  tr.setMeta("addToHistory", false);
  editor.view.dispatch(tr);
}

/** A markdown serializer state, the part of prosemirror-markdown's API the node uses. */
interface MarkdownSerializerState {
  write: (content: string) => void;
}

function shouldConvert(transactions: readonly Transaction[]): boolean {
  return transactions.some((tr) => tr.docChanged && (tr.getMeta("uiEvent") === "paste" || tr.getMeta(CHAT_CONVERT_MESSAGE_LINKS_META)));
}

/**
 * A link to a chat message as a chip in the composer, the way a ticket
 * reference is one: pasted links become chips at once. The message keeps
 * the URL as markdown's autolink (`<url>`), so the text sent is the link
 * itself and its end is unambiguous even when text follows it directly.
 */
export const ChatMessageLinkNode = Node.create({
  name: "chatMessageLink",
  group: "inline",
  inline: true,
  atom: true,
  selectable: true,

  addAttributes() {
    return { href: { default: "" } };
  },

  parseHTML() {
    return [
      {
        tag: "span[data-chat-message-link]",
        getAttrs: (element) => ({ href: (element as HTMLElement).getAttribute("data-chat-message-link") ?? "" }),
      },
    ];
  },

  renderHTML({ node }) {
    return ["span", { "data-chat-message-link": String(node.attrs.href ?? "") }, String(node.attrs.href ?? "")];
  },

  renderText({ node }) {
    return String(node.attrs.href ?? "");
  },

  addNodeView() {
    return ReactNodeViewRenderer(ChatMessageLinkNodeView, { as: "span" });
  },

  addStorage() {
    return {
      markdown: {
        serialize(state: MarkdownSerializerState, node: ProseMirrorNode) {
          state.write(`<${String(node.attrs.href ?? "")}>`);
        },
        parse: {},
      },
    };
  },

  addProseMirrorPlugins() {
    return [
      new Plugin({
        key: new PluginKey("chatMessageLinkPaste"),
        appendTransaction: (transactions: readonly Transaction[], _old: EditorState, state: EditorState) => {
          if (!shouldConvert(transactions)) return null;
          const tr = state.tr;
          return convertChatMessageLinks(tr) ? tr : null;
        },
      }),
    ];
  },
});
