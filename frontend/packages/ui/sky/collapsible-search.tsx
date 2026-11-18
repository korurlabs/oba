"use client";

import * as React from "react";
import { Search } from "lucide-react";

import { Button } from "./button";
import { Input } from "./field";
import { cn } from "../src/lib/utils";

/** What opens or closes the field: a click or the `/` key, Escape, or focus leaving it. */
export type CollapsibleSearchEvent =
  { type: "expand" } | { type: "escape" } | { type: "blur"; value: string };

/** Whether the field is expanded after `event`. Leaving it keeps it open while it holds text. */
export function nextExpanded(expanded: boolean, event: CollapsibleSearchEvent): boolean {
  switch (event.type) {
    case "expand":
      return true;
    case "escape":
      return false;
    case "blur":
      return event.value !== "" && expanded;
  }
}

/** The input shows while it is expanded or holds text, so an active search never hides. */
export function isSearchOpen(expanded: boolean, value: string): boolean {
  return expanded || value !== "";
}

type ShortcutTarget = { tagName?: string; isContentEditable?: boolean } | null;

/**
 * Whether a key press is the `/` shortcut: a bare `/` typed outside every
 * text field, so typing a slash into another input never steals the focus.
 */
export function isSearchShortcut(event: {
  key: string;
  ctrlKey: boolean;
  metaKey: boolean;
  altKey: boolean;
  target: EventTarget | ShortcutTarget;
}): boolean {
  if (event.key !== "/" || event.ctrlKey || event.metaKey || event.altKey) return false;
  const target = event.target as ShortcutTarget;
  if (target === null) return true;
  if (target.isContentEditable) return false;
  const tag = target.tagName?.toLowerCase();
  return tag !== "input" && tag !== "textarea" && tag !== "select";
}

export type CollapsibleSearchProps = {
  value: string;
  onChange: (value: string) => void;
  /** Names both the icon button and the input for assistive technology. */
  label: string;
  placeholder?: string;
  className?: string;
};

/**
 * A search icon button that expands into a search input on a click or on `/`.
 * Escape clears and collapses it; leaving it collapses it only when it is empty.
 */
export function CollapsibleSearch({
  value,
  onChange,
  label,
  placeholder,
  className,
}: CollapsibleSearchProps) {
  const [expanded, setExpanded] = React.useState(false);
  const inputRef = React.useRef<HTMLInputElement>(null);
  const buttonRef = React.useRef<HTMLButtonElement>(null);
  const returnFocus = React.useRef(false);
  const open = isSearchOpen(expanded, value);

  React.useEffect(() => {
    if (expanded) inputRef.current?.focus();
  }, [expanded]);

  React.useEffect(() => {
    if (!open && returnFocus.current) {
      returnFocus.current = false;
      buttonRef.current?.focus();
    }
  }, [open]);

  React.useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (!isSearchShortcut(event)) return;
      event.preventDefault();
      setExpanded((current: boolean) => nextExpanded(current, { type: "expand" }));
      inputRef.current?.focus();
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, []);

  if (!open) {
    return (
      <Button
        ref={buttonRef}
        type="button"
        variant="ghost"
        size="icon"
        aria-label={label}
        title={`${label} (/)`}
        className={className}
        onClick={() => setExpanded((current: boolean) => nextExpanded(current, { type: "expand" }))}
      >
        <Search aria-hidden />
      </Button>
    );
  }
  return (
    <div className={cn("relative w-full max-w-sm", className)}>
      <Search
        aria-hidden
        className="text-muted-foreground pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2"
      />
      <Input
        ref={inputRef}
        type="search"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        onKeyDown={(event) => {
          if (event.key !== "Escape") return;
          event.preventDefault();
          returnFocus.current = true;
          onChange("");
          setExpanded((current: boolean) => nextExpanded(current, { type: "escape" }));
        }}
        onBlur={(event) =>
          setExpanded((current: boolean) =>
            nextExpanded(current, { type: "blur", value: event.target.value })
          )
        }
        placeholder={placeholder}
        aria-label={label}
        className="pl-8"
      />
    </div>
  );
}
