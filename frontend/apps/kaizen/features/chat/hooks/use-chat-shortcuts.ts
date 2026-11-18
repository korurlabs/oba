"use client";

import { useEffect, useRef, type RefObject } from "react";
import type { ChatComposerHandle } from "../components/chat-composer";
import type { ChatMessageListHandle } from "../components/chat-message-list";
import { CHAT_MESSAGE_ROW_ATTRIBUTE } from "../components/chat-message-actions";
import {
  CHAT_CHANNEL_SHORTCUTS,
  CHAT_THREAD_PANEL_ATTRIBUTE,
  findChatShortcut,
} from "../lib/chat-shortcuts";

/** Open overlays that pause the chat's keys: dialogs, popovers, menus and listboxes. */
const OPEN_OVERLAY_SELECTOR = ["dialog", "alertdialog", "menu", "listbox"]
  .map((role) => `[role="${role}"][data-state="open"]`)
  .join(", ");

/** Elements where a typed character (Space above all) belongs to the element, not to the composer. */
const INTERACTIVE_SELECTOR = [
  "a[href]",
  "button",
  "summary",
  "[role='button']",
  "[role='tab']",
  "[role='menuitem']",
  "[role='option']",
  "[role='checkbox']",
  "[role='switch']",
  "[role='slider']",
  "[role='separator'][tabindex]",
].join(", ");

/** The input guard of the app's global shortcuts (`hooks/use-shortcuts.ts`). */
function isTextInput(target: Element): boolean {
  return (
    (target instanceof HTMLElement && target.isContentEditable) ||
    target.tagName === "INPUT" ||
    target.tagName === "TEXTAREA" ||
    target.tagName === "SELECT"
  );
}

function overlayOpen(): boolean {
  for (const overlay of document.querySelectorAll(OPEN_OVERLAY_SELECTOR)) {
    // The thread's own sheet on a phone is where the reader works, not an interruption.
    if (!overlay.hasAttribute(CHAT_THREAD_PANEL_ATTRIBUTE)) return true;
  }
  return false;
}

export interface ChatShortcutTargets {
  /** The chat view's element; Mod+F opens the search only while focus is inside it (or the thread panel). */
  rootRef: RefObject<HTMLElement | null>;
  channelList: RefObject<ChatMessageListHandle | null>;
  channelComposer: RefObject<ChatComposerHandle | null>;
  threadList: RefObject<ChatMessageListHandle | null>;
  threadComposer: RefObject<ChatComposerHandle | null>;
  onSearch: () => void;
  onShortcuts: () => void;
}

/**
 * The chat view's keys outside the composer, from `CHAT_CHANNEL_SHORTCUTS`:
 * a printable character starts a message, Shift+PageUp shows the "New
 * messages" divider, Alt+Shift+ArrowUp and ArrowDown move between messages,
 * Mod+F opens the search and Mod+/ the shortcuts dialog. Keys inside the
 * thread panel act on the thread. Nothing fires while a dialog, popover or
 * menu is open, and character keys never fire in an input or editor. The
 * keys of a focused message are the row's own, so a key it took arrives
 * here already handled.
 */
export function useChatShortcuts(targets: ChatShortcutTargets): void {
  const targetsRef = useRef(targets);
  useEffect(() => {
    targetsRef.current = targets;
  });

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.defaultPrevented || event.isComposing) return;
      const shortcut = findChatShortcut(event, CHAT_CHANNEL_SHORTCUTS);
      if (!shortcut || overlayOpen()) return;

      const { rootRef, channelList, channelComposer, threadList, threadComposer, onSearch, onShortcuts } =
        targetsRef.current;
      const target = event.target instanceof Element ? event.target : document.body;
      const inThread = !!target.closest(`[${CHAT_THREAD_PANEL_ATTRIBUTE}]`);
      const inChat = inThread || !!rootRef.current?.contains(target);

      if (shortcut === "shortcuts" || shortcut === "search") {
        if (!inChat) return;
        event.preventDefault();
        if (shortcut === "shortcuts") onShortcuts();
        else onSearch();
        return;
      }

      // Every other key only outside inputs, in the chat view or with nothing focused.
      if (isTextInput(target) || !(inChat || target === document.body)) return;
      const list = inThread ? threadList.current : channelList.current;

      switch (shortcut) {
        case "typeToCompose": {
          if (target.closest(INTERACTIVE_SELECTOR)) return;
          const composer = inThread ? threadComposer.current : channelComposer.current;
          if (!composer) return;
          event.preventDefault();
          composer.typeText(event.key);
          return;
        }
        case "jumpToUnread":
          if (channelList.current?.jumpToUnread()) event.preventDefault();
          return;
        case "previousMessage":
        case "nextMessage": {
          event.preventDefault();
          const current = target.closest<HTMLElement>(`[${CHAT_MESSAGE_ROW_ATTRIBUTE}]`);
          const log = current?.closest('[role="log"]');
          if (!current || !log) {
            list?.focusNewest();
            return;
          }
          const rows = Array.from(log.querySelectorAll<HTMLElement>(`[${CHAT_MESSAGE_ROW_ATTRIBUTE}]`));
          const index = rows.indexOf(current);
          rows[shortcut === "previousMessage" ? index - 1 : index + 1]?.focus();
          return;
        }
      }
    }

    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);
}
