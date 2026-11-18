/**
 * The keyboard shortcuts of the chat, in one list: the composer keymap, the
 * channel and message key handlers and the shortcuts dialog all read it, so
 * the dialog never shows a binding the handlers do not have.
 *
 * Keys are matched on `KeyboardEvent.code`, as the app's global shortcuts
 * are (`hooks/use-shortcuts.ts`), so a layout or an Option-key remapping on
 * macOS does not change them; "Mod" is Cmd or Ctrl, matched as either, as
 * there. The global Alt+N and Mod+K are not reused here.
 */

/** Matches any printable character, for "type to start a message". */
export const CHAT_ANY_CHARACTER = "AnyCharacter";

export interface ChatKeyBinding {
  /** `KeyboardEvent.code`, or `CHAT_ANY_CHARACTER`. */
  code: string;
  mod?: boolean;
  alt?: boolean;
  /** True requires Shift, false (the default) forbids it, "any" ignores it. */
  shift?: boolean | "any";
  /** Shown instead of the composed keys, for a key whose character differs from its code (Shift+= is "+"). */
  label?: string;
}

export type ChatComposerShortcutId =
  | "send"
  | "newLine"
  | "sendFromCode"
  | "codeIndent"
  | "editLast"
  | "escape"
  | "bold"
  | "italic"
  | "strike"
  | "inlineCode"
  | "codeBlock";

export type ChatChannelShortcutId =
  | "typeToCompose"
  | "jumpToUnread"
  | "previousMessage"
  | "nextMessage"
  | "search"
  | "shortcuts";

export type ChatMessageShortcutId =
  | "reply"
  | "edit"
  | "thread"
  | "pin"
  | "react"
  | "copy"
  | "delete"
  | "openThread"
  | "backToComposer";

export interface ChatShortcut<Id extends string> {
  id: Id;
  /** Each binding triggers the shortcut; the dialog lists them all. */
  bindings: readonly ChatKeyBinding[];
}

export const CHAT_COMPOSER_SHORTCUTS: readonly ChatShortcut<ChatComposerShortcutId>[] = [
  { id: "send", bindings: [{ code: "Enter" }] },
  { id: "newLine", bindings: [{ code: "Enter", shift: true }] },
  { id: "sendFromCode", bindings: [{ code: "Enter", mod: true }] },
  { id: "codeIndent", bindings: [{ code: "Tab" }] },
  { id: "editLast", bindings: [{ code: "ArrowUp" }] },
  { id: "escape", bindings: [{ code: "Escape" }] },
  { id: "bold", bindings: [{ code: "KeyB", mod: true }] },
  { id: "italic", bindings: [{ code: "KeyI", mod: true }] },
  { id: "strike", bindings: [{ code: "KeyX", mod: true, shift: true }] },
  { id: "inlineCode", bindings: [{ code: "KeyE", mod: true }] },
  { id: "codeBlock", bindings: [{ code: "KeyC", mod: true, alt: true }] },
];

export const CHAT_CHANNEL_SHORTCUTS: readonly ChatShortcut<ChatChannelShortcutId>[] = [
  { id: "typeToCompose", bindings: [{ code: CHAT_ANY_CHARACTER, shift: "any" }] },
  { id: "jumpToUnread", bindings: [{ code: "PageUp", shift: true }] },
  { id: "previousMessage", bindings: [{ code: "ArrowUp", alt: true, shift: true }] },
  { id: "nextMessage", bindings: [{ code: "ArrowDown", alt: true, shift: true }] },
  { id: "search", bindings: [{ code: "KeyF", mod: true }] },
  { id: "shortcuts", bindings: [{ code: "Slash", mod: true }] },
];

export const CHAT_MESSAGE_SHORTCUTS: readonly ChatShortcut<ChatMessageShortcutId>[] = [
  { id: "reply", bindings: [{ code: "KeyR" }] },
  { id: "edit", bindings: [{ code: "KeyE" }] },
  { id: "thread", bindings: [{ code: "KeyT" }] },
  { id: "pin", bindings: [{ code: "KeyP" }] },
  {
    id: "react",
    bindings: [
      { code: "Equal", shift: true, label: "+" },
      { code: "NumpadAdd", shift: "any", label: "+" },
    ],
  },
  { id: "copy", bindings: [{ code: "KeyC" }] },
  { id: "delete", bindings: [{ code: "Backspace", shift: "any" }, { code: "Delete", shift: "any" }] },
  { id: "openThread", bindings: [{ code: "Enter" }] },
  { id: "backToComposer", bindings: [{ code: "Escape" }] },
];

// ── Matching ────────────────────────────────────────────────────────

/** A key that types a character: one character, no Cmd, Ctrl or Alt, not part of an IME composition. */
export function isChatPrintableKey(event: KeyboardEvent): boolean {
  return event.key.length === 1 && !event.metaKey && !event.ctrlKey && !event.altKey && !event.isComposing;
}

export function matchesChatBinding(event: KeyboardEvent, binding: ChatKeyBinding): boolean {
  const mod = event.metaKey || event.ctrlKey;
  const shiftMatches = binding.shift === "any" || !!binding.shift === event.shiftKey;
  if (binding.code === CHAT_ANY_CHARACTER) return isChatPrintableKey(event) && shiftMatches;
  return (
    event.code === binding.code && !!binding.mod === mod && !!binding.alt === event.altKey && shiftMatches
  );
}

/** The first shortcut of `shortcuts` that `event` triggers. */
export function findChatShortcut<Id extends string>(
  event: KeyboardEvent,
  shortcuts: readonly ChatShortcut<Id>[],
): Id | null {
  for (const shortcut of shortcuts) {
    if (shortcut.bindings.some((binding) => matchesChatBinding(event, binding))) return shortcut.id;
  }
  return null;
}

// ── Tiptap keymap ───────────────────────────────────────────────────

/** ProseMirror's name for a key code: `KeyB` is `b`, named keys keep their name. */
function tiptapKeyName(code: string): string {
  const letter = /^Key([A-Z])$/.exec(code);
  if (letter?.[1]) return letter[1].toLowerCase();
  const digit = /^Digit(\d)$/.exec(code);
  if (digit?.[1]) return digit[1];
  return code;
}

function toTiptapKey(binding: ChatKeyBinding): string {
  const parts: string[] = [];
  if (binding.mod) parts.push("Mod");
  if (binding.alt) parts.push("Alt");
  if (binding.shift === true) parts.push("Shift");
  parts.push(tiptapKeyName(binding.code));
  return parts.join("-");
}

/**
 * The composer's Tiptap keyboard shortcuts, built from the list: each
 * binding of a composer shortcut runs its handler.
 */
export function chatComposerKeymap(
  handlers: Record<ChatComposerShortcutId, () => boolean>,
): Record<string, () => boolean> {
  const keymap: Record<string, () => boolean> = {};
  for (const shortcut of CHAT_COMPOSER_SHORTCUTS) {
    for (const binding of shortcut.bindings) keymap[toTiptapKey(binding)] = handlers[shortcut.id];
  }
  return keymap;
}

// ── Display ─────────────────────────────────────────────────────────

/** The names of keys that are words, translated by the dialog. */
export type ChatKeyWord = "enter" | "escape" | "tab" | "pageUp" | "backspace" | "delete" | "anyCharacter";

const KEY_WORDS: Partial<Record<string, ChatKeyWord>> = {
  Enter: "enter",
  Escape: "escape",
  Tab: "tab",
  PageUp: "pageUp",
  Backspace: "backspace",
  Delete: "delete",
  [CHAT_ANY_CHARACTER]: "anyCharacter",
};

const KEY_SYMBOLS: Partial<Record<string, string>> = {
  ArrowUp: "↑",
  ArrowDown: "↓",
  Slash: "/",
  Equal: "=",
  NumpadAdd: "+",
};

/** One key of a binding as the dialog shows it: a symbol or letter, or a word to translate. */
export type ChatKeyPart = { text: string } | { word: ChatKeyWord };

/** The keys of a binding for the reader's platform: ⌘ ⌥ ⇧ on macOS, Ctrl Alt Shift elsewhere. */
export function chatBindingParts(binding: ChatKeyBinding, isMac: boolean): ChatKeyPart[] {
  if (binding.label) return [{ text: binding.label }];
  const parts: ChatKeyPart[] = [];
  if (binding.mod) parts.push({ text: isMac ? "⌘" : "Ctrl" });
  if (binding.alt) parts.push({ text: isMac ? "⌥" : "Alt" });
  if (binding.shift === true) parts.push({ text: isMac ? "⇧" : "Shift" });
  const word = KEY_WORDS[binding.code];
  if (word) {
    parts.push({ word });
  } else {
    const letter = /^Key([A-Z])$/.exec(binding.code);
    parts.push({ text: letter?.[1] ?? KEY_SYMBOLS[binding.code] ?? binding.code });
  }
  return parts;
}

/** True on macOS and iOS, where Mod is ⌘. Only call it in the browser. */
export function isApplePlatform(): boolean {
  const platform =
    (navigator as Navigator & { userAgentData?: { platform?: string } }).userAgentData?.platform ??
    navigator.platform ??
    "";
  return /mac|iphone|ipad|ipod/i.test(platform);
}

// ── Scope ───────────────────────────────────────────────────────────

/**
 * Marks the thread panel's root, which may live outside the chat view's
 * element (a sheet on a phone): keys pressed inside it act on the thread's
 * list and composer, and its sheet does not count as an open dialog.
 */
export const CHAT_THREAD_PANEL_ATTRIBUTE = "data-chat-thread-panel";
