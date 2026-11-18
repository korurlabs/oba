"use client";

import { useCallback, useState, type ReactNode } from "react";
import { useLocale, useTranslations } from "next-intl";
import { EmojiPicker, type Locale as EmojiLocale } from "frimousse";
import { cn } from "@oba/ui/lib/utils";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";

/**
 * The Emojibase data the picker reads, served by the app itself from
 * `public/emojibase/<locale>/{data,messages}.json`, so the picker does not
 * depend on a CDN being reachable.
 */
const EMOJIBASE_URL = "/emojibase";

/** The app locales with emoji names in the served data; any other locale searches the English names. */
const EMOJI_LOCALES: Partial<Record<string, EmojiLocale>> = { en: "en", nl: "nl" };
const DEFAULT_EMOJI_LOCALE: EmojiLocale = "en";

const RECENT_STORAGE_KEY = "kazan.chat.recent-emoji";
const RECENT_MAX = 16;

/**
 * The reader's recently used emoji, newest first. Kept in this browser only;
 * without storage (private window, blocked site data) the row stays empty.
 */
function readRecentEmoji(): string[] {
  try {
    const raw = window.localStorage.getItem(RECENT_STORAGE_KEY);
    const parsed: unknown = raw ? JSON.parse(raw) : [];
    if (!Array.isArray(parsed)) return [];
    return parsed.filter((item): item is string => typeof item === "string").slice(0, RECENT_MAX);
    // eslint-disable-next-line no-empty -- reason: the recent row is a per-browser convenience; unreadable storage only means no recent emoji are offered
  } catch {}
  return [];
}

function writeRecentEmoji(recent: readonly string[]): void {
  try {
    window.localStorage.setItem(RECENT_STORAGE_KEY, JSON.stringify(recent));
    // eslint-disable-next-line no-empty -- reason: the recent row is a per-browser convenience; when storage refuses the write, the emoji was still picked
  } catch {}
}

interface ChatEmojiPickerProps {
  /** Called with the chosen emoji, skin tone applied. */
  onSelect: (emoji: string) => void;
  /** The button that opens the picker. */
  children: ReactNode;
  /** Controlled open state; uncontrolled when omitted. */
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
  align?: "start" | "center" | "end";
  side?: "top" | "right" | "bottom" | "left";
  /** Where focus goes when the picker closes; by default back to the button. */
  onCloseAutoFocus?: (event: Event) => void;
}

/**
 * An emoji picker in a popover: search, categories, skin tone and the
 * reader's recent emoji. Closes after a pick.
 */
export function ChatEmojiPicker({
  onSelect,
  children,
  open: openProp,
  onOpenChange,
  align = "end",
  side = "top",
  onCloseAutoFocus,
}: ChatEmojiPickerProps) {
  const t = useTranslations("chat.emoji");
  const locale = useLocale();
  const [openState, setOpenState] = useState(false);
  const open = openProp ?? openState;
  const [recent, setRecent] = useState<string[]>([]);

  const setOpen = useCallback(
    (next: boolean) => {
      if (next) setRecent(readRecentEmoji());
      setOpenState(next);
      onOpenChange?.(next);
    },
    [onOpenChange],
  );

  const pick = useCallback(
    (emoji: string) => {
      const nextRecent = [emoji, ...readRecentEmoji().filter((item) => item !== emoji)].slice(0, RECENT_MAX);
      writeRecentEmoji(nextRecent);
      setRecent(nextRecent);
      onSelect(emoji);
      setOpen(false);
    },
    [onSelect, setOpen],
  );

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>{children}</PopoverTrigger>
      <PopoverContent align={align} side={side} onCloseAutoFocus={onCloseAutoFocus} className="w-fit p-0">
        <EmojiPicker.Root
          locale={EMOJI_LOCALES[locale] ?? DEFAULT_EMOJI_LOCALE}
          emojibaseUrl={EMOJIBASE_URL}
          columns={9}
          onEmojiSelect={(emoji) => pick(emoji.emoji)}
          className="bg-popover isolate flex h-[360px] w-fit flex-col"
        >
          <EmojiPicker.Search
            aria-label={t("search")}
            placeholder={t("search")}
            className="bg-muted text-foreground placeholder:text-muted-foreground z-10 mx-2 mt-2 appearance-none rounded-md px-2.5 py-1.5 text-sm outline-none"
          />
          {recent.length > 0 && (
            <div className="border-border border-b px-2 pt-2 pb-1.5">
              <p className="text-muted-foreground px-1 pb-1 text-xs font-medium">{t("recent")}</p>
              <div className="flex flex-wrap">
                {recent.map((emoji) => (
                  <button
                    key={emoji}
                    type="button"
                    onClick={() => pick(emoji)}
                    aria-label={emoji}
                    className="hover:bg-accent focus-visible:ring-ring flex size-8 items-center justify-center rounded-md text-lg focus-visible:ring-2 focus-visible:outline-none"
                  >
                    {emoji}
                  </button>
                ))}
              </div>
            </div>
          )}
          <EmojiPicker.Viewport className="relative flex-1 outline-none">
            <EmojiPicker.Loading className="text-muted-foreground absolute inset-0 flex items-center justify-center text-sm">
              {t("loading")}
            </EmojiPicker.Loading>
            <EmojiPicker.Empty className="text-muted-foreground absolute inset-0 flex items-center justify-center text-sm">
              {t("empty")}
            </EmojiPicker.Empty>
            <EmojiPicker.List
              className="pb-1.5 select-none"
              components={{
                CategoryHeader: ({ category, ...props }) => (
                  <div className="bg-popover text-muted-foreground px-3 pt-3 pb-1.5 text-xs font-medium" {...props}>
                    {category.label}
                  </div>
                ),
                Row: ({ children: row, ...props }) => (
                  <div className="scroll-my-1.5 px-1.5" {...props}>
                    {row}
                  </div>
                ),
                Emoji: ({ emoji, className, ...props }) => (
                  <button
                    className={cn(
                      "flex size-8 items-center justify-center rounded-md text-lg data-[active]:bg-accent",
                      className,
                    )}
                    {...props}
                  >
                    {emoji.emoji}
                  </button>
                ),
              }}
            />
          </EmojiPicker.Viewport>
          <div className="border-border flex h-10 max-w-full items-center justify-between gap-2 border-t px-2">
            <EmojiPicker.ActiveEmoji>
              {({ emoji }) =>
                emoji ? (
                  <div className="flex min-w-0 items-center gap-1.5">
                    <span className="text-lg">{emoji.emoji}</span>
                    <span className="text-muted-foreground truncate text-xs">{emoji.label}</span>
                  </div>
                ) : (
                  <span className="text-muted-foreground truncate text-xs">{t("hint")}</span>
                )
              }
            </EmojiPicker.ActiveEmoji>
            <EmojiPicker.SkinToneSelector
              aria-label={t("skinTone")}
              title={t("skinTone")}
              className="hover:bg-accent flex size-8 shrink-0 items-center justify-center rounded-md text-lg"
            />
          </div>
        </EmojiPicker.Root>
      </PopoverContent>
    </Popover>
  );
}
