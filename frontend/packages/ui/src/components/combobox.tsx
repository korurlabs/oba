"use client";

import { CheckIcon, ChevronsUpDown } from "lucide-react";
import * as React from "react";
import { useMemo } from "react";

import { Button } from "./button";
import {
    Command,
    CommandEmpty,
    CommandGroup,
    CommandInput,
    CommandItem,
    CommandList,
} from "./command";
import { Popover, PopoverContent, PopoverTrigger } from "./popover";
import { cn } from "@oba/ui/lib/utils";

type ComboboxOption = {
  value: string;
  label: string;
  disabled?: boolean;
};

type ComboboxProps = {
  options?: ComboboxOption[];
  value?: string | null | undefined;
  onValueChange?: (value: string) => void;
  placeholder?: string;
  searchPlaceholder?: string;
  emptyMessage?: string;
  className?: string; // Trigger button class
  contentClassName?: string; // Popover content class
  disabled?: boolean;
  clearable?: boolean; // Clicking selected item clears the value
};

function Combobox({
  options,
  value,
  onValueChange,
  placeholder = "Select option...",
  searchPlaceholder = "Search...",
  emptyMessage = "No results found.",
  className,
  contentClassName,
  disabled,
  clearable = true,
}: ComboboxProps) {
  const [searchTerm, setSearchTerm] = React.useState("");
  const [open, setOpen] = React.useState(false);

  const selected = value != null ? (options?.find((opt) => opt.value === value) ?? null) : null;

  const filteredOptions = useMemo(
    () => options?.filter((opt) => opt.label.toLowerCase().includes(searchTerm.toLowerCase())),
    [options, searchTerm]
  );

  function handleSelect(nextValue: string) {
    if (selected?.value === nextValue && clearable) {
      onValueChange?.("");
    } else {
      onValueChange?.(nextValue);
    }
    setOpen(false);
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          role="combobox"
          aria-expanded={open}
          className={cn("min-h-11 rounded-lg border-[1.5px] px-4 py-2 justify-between", disabled && "opacity-50 cursor-not-allowed", className)}
          disabled={disabled}
        >
          {selected ? selected.label : placeholder}
          <ChevronsUpDown className="opacity-50" size={10} />
        </Button>
      </PopoverTrigger>

      <PopoverContent
        className={cn("p-0 !w-(--radix-popover-trigger-width)", contentClassName)}
        align="start"
        sideOffset={4}
      >
        <Command shouldFilter={false}>
          <div className="relative border-b w-full">
            <CommandInput
              placeholder={searchPlaceholder}
              value={searchTerm}
              onValueChange={(v) => setSearchTerm(v)}
            />
          </div>

          <CommandList>
            <CommandEmpty>{emptyMessage}</CommandEmpty>
            <CommandGroup>
              {filteredOptions?.map((opt) => (
                <CommandItem
                  key={opt.value}
                  value={opt.value}
                  disabled={opt.disabled}
                  onSelect={handleSelect}
                >
                  {opt.label}
                  <CheckIcon
                    className={cn(
                      "ml-auto size-4",
                      value === opt.value ? "opacity-100" : "opacity-0"
                    )}
                  />
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}

export { Combobox };
export type { ComboboxOption, ComboboxProps };

