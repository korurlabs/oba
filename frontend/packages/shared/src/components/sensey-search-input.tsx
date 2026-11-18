"use client";

import { Input } from "@oba/ui/components/input";
import { useDebounce } from "../hooks/use-debounce";
import { Loader2, Search, X } from "lucide-react";
import { useTranslations } from "next-intl";
import { memo, useCallback, useEffect, useRef, useState } from "react";
import { cn } from "@oba/ui/lib/utils";

const sizeClasses = {
  sm: "min-h-9 max-h-9",
  md: "min-h-10 max-h-10",
  lg: "min-h-12 max-h-12",
} as const;

interface SenseySearchInputProps {
  value?: string | null;
  placeholder?: string;
  onSearchChange?: (value: string | undefined) => void;
  size?: keyof typeof sizeClasses;
  className?: string;
  /**
   * Ref to the underlying input element. Use it to restore focus to the search
   * box after an interaction elsewhere in the same popover/panel unmounted the
   * element the user clicked, so they can keep typing without an extra click.
   */
  inputRef?: React.Ref<HTMLInputElement>;
  /**
   * Increment this to clear the typed text imperatively.
   *
   * Clearing through `value` alone is not reliable: the text the user sees is
   * held in this component's own state and is only pushed upward after the
   * debounce, so when the parent's `value` is already "" (debounce still
   * pending) setting it to "" again is a no-op and the stale text stays on
   * screen. Bumping this signal always resets, and does so without remounting,
   * so focus and the caret are preserved.
   */
  resetSignal?: number;
}

function SenseySearchInputComponent({
  placeholder,
  onSearchChange,
  value,
  size = "sm",
  className,
  inputRef,
  resetSignal,
}: SenseySearchInputProps) {
  const t = useTranslations();
  const [searchValue, setSearchValue] = useState<string>(value ?? "");
  const { debouncedValue, loading } = useDebounce(searchValue, 500);

  useEffect(() => {
    const trimmedValue = debouncedValue?.trim() ?? "";
    onSearchChange?.(trimmedValue === "" ? undefined : trimmedValue);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedValue]);

  useEffect(() => {
    const incomingValue = value ?? "";
    // Only adopt an external value. When the incoming value is just the trimmed
    // echo of what is already typed (a consumer round-tripping our own
    // onSearchChange back in), keep the raw text so the user's in-progress
    // whitespace is not stripped out from under them.
    setSearchValue((currentValue) =>
      currentValue.trim() === incomingValue ? currentValue : incomingValue,
    );
  }, [value]);

  // Imperative reset. Skipped on mount so that simply passing a `resetSignal`
  // never wipes an initial `value`.
  const hasSeenResetSignal = useRef(false);
  useEffect(() => {
    if (!hasSeenResetSignal.current) {
      hasSeenResetSignal.current = true;
      return;
    }
    setSearchValue("");
  }, [resetSignal]);

  const handleClear = useCallback(() => {
    setSearchValue("");
    onSearchChange?.(undefined);
  }, [onSearchChange]);

  const handleChange = useCallback(
    (event: React.ChangeEvent<HTMLInputElement>) => {
      setSearchValue(event.target.value);
    },
    [],
  );

  return (
    <div className={cn("relative flex items-center", className)}>
      <Search className="absolute left-2.5 h-4 w-4" />
      <Input
        ref={inputRef}
        aria-label="Search input"
        placeholder={placeholder ?? t("search")}
        value={searchValue}
        onChange={handleChange}
        className={cn(sizeClasses[size], "w-full pl-8 pr-8")}
      />
      {searchValue.length > 0 && loading ? (
        <Loader2 className="absolute right-2.5 h-4 w-4 animate-spin text-muted-foreground" />
      ) : (
        searchValue && (
          <X
            className="absolute right-2.5 h-4 w-4 cursor-pointer text-muted-foreground hover:text-foreground"
            onClick={handleClear}
          />
        )
      )}
    </div>
  );
}

export const SenseySearchInput = memo(SenseySearchInputComponent);
