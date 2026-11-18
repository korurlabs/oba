"use client";

import { Fragment, useSyncExternalStore } from "react";
import { useTranslations } from "next-intl";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import {
  CHAT_CHANNEL_SHORTCUTS,
  CHAT_COMPOSER_SHORTCUTS,
  CHAT_MESSAGE_SHORTCUTS,
  chatBindingParts,
  isApplePlatform,
  type ChatKeyBinding,
} from "../lib/chat-shortcuts";

/** The platform never changes while the page is open. */
const subscribeNever = () => () => undefined;
/** The server renders Ctrl; the browser corrects it to ⌘ on Apple devices. */
const serverIsApple = () => false;

interface ShortcutItem {
  id: string;
  label: string;
  bindings: readonly ChatKeyBinding[];
}

function Keys({ binding, isMac }: { binding: ChatKeyBinding; isMac: boolean }) {
  const t = useTranslations("chat.shortcuts.keys");
  return (
    <span className="inline-flex items-center gap-1">
      {chatBindingParts(binding, isMac).map((part, index) => (
        <kbd
          key={index}
          className="border-border bg-muted text-foreground inline-flex h-6 min-w-6 items-center justify-center rounded border px-1.5 font-mono text-[11px] font-medium"
        >
          {"word" in part ? t(part.word) : part.text}
        </kbd>
      ))}
    </span>
  );
}

function ShortcutGroup({ title, items, isMac }: { title: string; items: ShortcutItem[]; isMac: boolean }) {
  const t = useTranslations("chat.shortcuts");
  return (
    <section className="flex flex-col gap-2">
      <h3 className="text-muted-foreground text-xs font-semibold uppercase">{title}</h3>
      <dl className="flex flex-col gap-1.5">
        {items.map((item) => (
          <div key={item.id} className="flex items-center justify-between gap-4 text-sm">
            <dt className="text-foreground min-w-0">{item.label}</dt>
            <dd className="flex shrink-0 flex-wrap items-center justify-end gap-1.5">
              {item.bindings.map((binding, index) => (
                <Fragment key={`${binding.code}-${index}`}>
                  {index > 0 && <span className="text-muted-foreground text-xs">{t("or")}</span>}
                  <Keys binding={binding} isMac={isMac} />
                </Fragment>
              ))}
            </dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

interface ChatShortcutsDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * Every keyboard shortcut of the chat, read from the same lists the key
 * handlers use, with the keys of the reader's platform (⌘ ⌥ ⇧ or Ctrl Alt
 * Shift).
 */
export function ChatShortcutsDialog({ open, onOpenChange }: ChatShortcutsDialogProps) {
  const t = useTranslations("chat.shortcuts");
  const isMac = useSyncExternalStore(subscribeNever, isApplePlatform, serverIsApple);

  const composer = CHAT_COMPOSER_SHORTCUTS.map((shortcut) => ({
    id: shortcut.id,
    label: t(`composer.${shortcut.id}`),
    bindings: shortcut.bindings,
  }));
  const channel = CHAT_CHANNEL_SHORTCUTS.map((shortcut) => ({
    id: shortcut.id,
    label: t(`channel.${shortcut.id}`),
    bindings: shortcut.bindings,
  }));
  const message = CHAT_MESSAGE_SHORTCUTS.map((shortcut) => ({
    id: shortcut.id,
    label: t(`message.${shortcut.id}`),
    bindings: shortcut.bindings,
  }));

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[85vh] max-w-xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{t("title")}</DialogTitle>
          <DialogDescription>{t("description")}</DialogDescription>
        </DialogHeader>
        <div className="flex flex-col gap-5">
          <ShortcutGroup title={t("groups.composer")} items={composer} isMac={isMac} />
          <ShortcutGroup title={t("groups.channel")} items={channel} isMac={isMac} />
          <ShortcutGroup title={t("groups.message")} items={message} isMac={isMac} />
        </div>
      </DialogContent>
    </Dialog>
  );
}
