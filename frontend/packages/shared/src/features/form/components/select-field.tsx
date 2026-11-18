"use client";

import { useState, useMemo, useEffect, useRef, useId } from "react";
import { FieldValues } from "react-hook-form";
import { useTranslations } from "next-intl";
import { Check, ChevronDown, Loader2, Plus, X } from "lucide-react";
import { Popover, PopoverTrigger, PopoverContent } from "@oba/ui/components/popover";
import { Button } from "@oba/ui/components/button";
import { Label } from "@oba/ui/components/label";
import {
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@oba/ui/components/form";
import { cn } from "@oba/ui/lib/utils";
import { useDebounce } from "@oba/shared/hooks/use-debounce";
import { SenseySearchInput } from "@oba/shared/components/sensey-search-input";
import { FormfieldProps } from "../types";

// ── Types ────────────────────────────────────────────────────────────

export type SelectOption = {
  value: string;
  label: string;
  disabled?: boolean;
  disabledLabel?: string;
  /** Arbitrary metadata for use in custom renderOption callbacks */
  meta?: Record<string, unknown>;
  /**
   * Heading of the dropdown section this option is listed under. Options
   * that share a `group` are rendered together below it, in the order the
   * groups first appear. A section left empty by the search is not shown.
   * When no option sets a group, the list is flat, exactly as before.
   */
  group?: string;
};

/** One dropdown section: a heading (if any) and the options under it. */
type OptionSection = { group: string | undefined; options: SelectOption[] };

/** Same look as a `CommandGroup` heading in `@oba/ui`. */
const GROUP_HEADING_CLASS = "text-muted-foreground px-2 py-1.5 text-xs font-medium";

/**
 * `vertical` (default) puts the label above the trigger. `horizontal` is a
 * label-left / value-right row for dense property panels.
 */
export type SelectFieldOrientation = "vertical" | "horizontal";

/**
 * Grid for a horizontal row. The 7rem label column matches the horizontal
 * form fields in the kazan app (`components/ui/form-fields`), so rows from
 * both packages line up when they share a panel.
 */
const HORIZONTAL_ROW_CLASS = "grid grid-cols-[7rem_minmax(0,1fr)] items-center gap-x-3 gap-y-1";

/** A narrow label column has to be allowed to wrap, so `leading-none` is relaxed. */
const HORIZONTAL_LABEL_CLASS = "text-muted-foreground font-normal leading-snug";

/** Any nuqs-style setter: setBoardFilters, setTicketFilters, etc. */
type FilterParamsSetter = (params: Record<string, string | null>) => void | Promise<unknown>;

type SelectFieldBaseProps = {
  options: SelectOption[];
  placeholder?: string;
  searchPlaceholder?: string;
  /** Pass the setter from any useXxxFilterParams hook — automatically updates { search } */
  setFilterParams?: FilterParamsSetter;
  /** Simple callback for local state search (use in dialogs to avoid URL param changes) */
  onSearchChange?: (search: string) => void;
  /** Custom renderer for dropdown options. Checkmark is still rendered by SelectField. */
  renderOption?: (option: SelectOption) => React.ReactNode;
  /**
   * Custom renderer for the selected value in the trigger. Falls back to
   * `renderOption`. Needed when an option renderer carries list-only layout,
   * such as tree indentation, that does not fit the trigger.
   */
  renderValue?: (option: SelectOption) => React.ReactNode;
  isLoading?: boolean;
  disabled?: boolean;
  className?: string;
  /**
   * Single-select only: clicking the option that is already selected clears
   * the value to an empty string, as the `Combobox` in `@oba/ui` does.
   * Off by default, so a required select cannot be emptied by accident.
   */
  clearable?: boolean;
  /** Called when the user wants to create a new option from the search term */
  onCreate?: (searchTerm: string) => void | Promise<void>;
  /** Custom label for the create button. Defaults to `Create "${searchTerm}"` */
  createLabel?: (searchTerm: string) => string;
  /** Loading state while creating a new option */
  isCreating?: boolean;
  /** Label position. Defaults to `vertical`. */
  orientation?: SelectFieldOrientation;
  /**
   * Extra classes for the dropdown, e.g. a width that is not tied to a
   * narrow trigger so long option labels stay readable.
   */
  contentClassName?: string;
};

// ── Standalone SelectFieldStandalone ─────────────────────────────────

type SelectFieldStandaloneSingleProps = SelectFieldBaseProps & {
  isMulti?: false;
  value: string;
  onValueChange: (value: string) => void;
  label?: string;
  required?: boolean;
};

type SelectFieldStandaloneMultiProps = SelectFieldBaseProps & {
  isMulti: true;
  value: string[];
  onValueChange: (value: string[]) => void;
  label?: string;
  required?: boolean;
};

type SelectFieldStandaloneProps = SelectFieldStandaloneSingleProps | SelectFieldStandaloneMultiProps;

export function SelectFieldStandalone(props: SelectFieldStandaloneProps) {
  const {
    options,
    label,
    required,
    placeholder,
    searchPlaceholder,
    setFilterParams,
    onSearchChange: onSearchChangeProp,
    renderOption,
    renderValue,
    isLoading = false,
    disabled = false,
    className,
    clearable = false,
    onCreate,
    createLabel,
    isCreating = false,
    orientation = "vertical",
    contentClassName,
  } = props;

  const t = useTranslations();
  const isMulti = props.isMulti === true;

  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");
  const searchInputRef = useRef<HTMLInputElement>(null);
  // Bumped to force the search box to clear. See resetSearchAfterSelect.
  const [searchResetSignal, setSearchResetSignal] = useState(0);

  /**
   * Clear the typed query after a selection has actually been applied, so the
   * list is immediately back to showing everything and the user can pick again
   * without clearing by hand. Deliberately NOT wired to open/close, blur or
   * unmount: a stray click or an Escape must leave the typed text alone.
   */
  const resetSearchAfterSelect = () => {
    setSearch("");
    setSearchResetSignal((signal) => signal + 1);
  };

  /**
   * Keep the caret in the search box when the popover stays open. The element
   * the user just clicked is about to unmount (a selected entry is filtered out
   * of the list, the create button disappears with the term), which would drop
   * focus to the body and cost an extra click before they could type again.
   * A no-op once the popover has closed, since the input is unmounted by then.
   */
  const refocusSearchInput = () => {
    searchInputRef.current?.focus();
  };

  const hasServerSearch = !!(setFilterParams || onSearchChangeProp);
  const { debouncedValue: debouncedSearch, loading: isDebouncing } = useDebounce(search, 300);
  const showLoading = isLoading || (hasServerSearch && isDebouncing);

  // Sync debounced search to server-side callback
  useEffect(() => {
    if (!hasServerSearch) return;
    if (setFilterParams) {
      setFilterParams({ search: debouncedSearch || null });
    } else {
      onSearchChangeProp?.(debouncedSearch);
    }
  }, [debouncedSearch, hasServerSearch, setFilterParams, onSearchChangeProp]);

  // ── Single-select helpers ──
  const singleValue = !isMulti ? (props as SelectFieldStandaloneSingleProps).value : "";
  const singleOnChange = !isMulti
    ? (props as SelectFieldStandaloneSingleProps).onValueChange
    : undefined;
  const selectedOption = !isMulti ? options.find((o) => o.value === singleValue) : undefined;
  const selectedLabel = selectedOption?.label;

  // ── Multi-select helpers ──
  const multiValue = isMulti ? (props as SelectFieldStandaloneMultiProps).value : [];
  const multiOnChange = isMulti
    ? (props as SelectFieldStandaloneMultiProps).onValueChange
    : undefined;

  const filteredOptions = useMemo(() => {
    let result = options;

    // Remove already-selected items from dropdown in multi-select mode
    if (isMulti) {
      result = result.filter((option) => !multiValue.includes(option.value));
    }

    // Skip client-side filtering when server-side search is active
    if (hasServerSearch || !search) return result;
    const lower = search.toLowerCase();
    return result.filter((option) => option.label.toLowerCase().includes(lower));
  }, [options, search, hasServerSearch, isMulti, multiValue]);

  // Sections are built from the FILTERED list, so a group the search has
  // emptied disappears together with its heading. `null` = flat list.
  const optionSections = useMemo((): OptionSection[] | null => {
    if (!filteredOptions.some((option) => option.group)) return null;
    const sections: OptionSection[] = [];
    for (const option of filteredOptions) {
      const section = sections.find((s) => s.group === option.group);
      if (section) section.options.push(option);
      else sections.push({ group: option.group, options: [option] });
    }
    return sections;
  }, [filteredOptions]);
  const groupIdPrefix = useId();

  const handleSelect = (optionValue: string) => {
    if (isMulti) {
      if (!multiOnChange) return;
      const current = multiValue;
      const isAdding = !current.includes(optionValue);
      const next = isAdding
        ? [...current, optionValue]
        : current.filter((v) => v !== optionValue);
      multiOnChange(next);
      // Don't close popover in multi-select mode
      if (isAdding) {
        resetSearchAfterSelect();
        refocusSearchInput();
      }
      // Toggling an already-selected entry off is a de-selection, not a
      // selection, so the query stays.
    } else {
      if (!singleOnChange) return;
      singleOnChange(clearable && singleValue === optionValue ? "" : optionValue);
      // The popover closes here, so no refocus: Radix returns focus to the
      // trigger on close and must not be fought for it.
      setOpen(false);
      resetSearchAfterSelect();
    }
  };

  const handleRemoveTag = (optionValue: string) => {
    if (isMulti) {
      multiOnChange?.(multiValue.filter((v) => v !== optionValue));
    }
  };

  const isSelected = (optionValue: string): boolean => {
    if (isMulti) return multiValue.includes(optionValue);
    return singleValue === optionValue;
  };

  // ── Create button logic ──
  const showCreateButton = useMemo(() => {
    if (!onCreate || !search.trim()) return false;
    const lower = search.trim().toLowerCase();
    return !options.some((o) => o.label.toLowerCase() === lower);
  }, [onCreate, search, options]);

  const handleCreate = async () => {
    if (!onCreate || !search.trim()) return;
    const term = search.trim();
    await onCreate(term);
    resetSearchAfterSelect();
    refocusSearchInput();
  };

  // Trigger label for multi-select
  const multiSelectedLabels = isMulti
    ? multiValue.map((v) => options.find((o) => o.value === v)).filter(Boolean)
    : [];

  const renderTriggerValue = renderValue ?? renderOption;

  const renderOptionButton = (option: SelectOption) => (
    <button
      key={option.value}
      type="button"
      disabled={option.disabled}
      className={cn(
        "hover:bg-accent hover:text-accent-foreground relative flex w-full cursor-default items-center rounded-sm py-1.5 pr-8 pl-2 text-sm transition-colors outline-none select-none",
        isSelected(option.value) && "bg-accent/50",
        option.disabled && "cursor-not-allowed opacity-50"
      )}
      onClick={() => !option.disabled && handleSelect(option.value)}
    >
      <span className="flex-1 truncate text-left">
        {renderOption ? (
          renderOption(option)
        ) : (
          <>
            {option.label}
            {option.disabled && option.disabledLabel && (
              <span className="text-muted-foreground ml-2 text-xs">
                ({option.disabledLabel})
              </span>
            )}
          </>
        )}
      </span>
      <span className="absolute right-2 flex h-3.5 w-3.5 items-center justify-center">
        <Check
          className={cn(
            "h-4 w-4",
            isSelected(option.value) ? "opacity-100" : "opacity-0"
          )}
        />
      </span>
    </button>
  );

  const isHorizontal = orientation === "horizontal";

  return (
    <div
      className={cn(
        "w-full",
        isHorizontal && label ? HORIZONTAL_ROW_CLASS : "flex flex-col gap-2 overflow-hidden"
      )}
    >
      {label && (
        <div className={cn("flex items-center gap-1 leading-5", isHorizontal && "min-w-0")}>
          <Label className={cn(isHorizontal && HORIZONTAL_LABEL_CLASS)}>{label}</Label>
          {required ? <span className="text-destructive">*</span> : null}
        </div>
      )}
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <Button
            type="button"
            variant="outline"
            role="combobox"
            aria-expanded={open}
            disabled={disabled}
            className={cn(
              "border-input bg-background dark:bg-input/30 text-foreground flex h-auto min-h-11 w-full items-center justify-between rounded-lg border-[1.5px] px-4 py-2 text-sm whitespace-nowrap shadow-xs transition-all duration-150",
              "hover:border-border dark:hover:bg-input/50",
              "focus:border-primary focus:ring-primary/20 outline-none focus:ring-[3px]",
              open && "border-primary ring-primary/20 ring-[3px]",
              "disabled:bg-muted disabled:cursor-not-allowed disabled:opacity-50",
              !renderTriggerValue && "[&>span]:line-clamp-1",
              !isMulti && !selectedLabel && "text-muted-foreground",
              isMulti && multiValue.length === 0 && "text-muted-foreground",
              className
            )}
          >
            {/* `w-0 flex-1`: the value takes the width the trigger has left
                beside the chevron, but adds nothing to the trigger's own
                minimum width. Otherwise a long, unwrapped value widens the
                trigger, and an auto-sized dialog with it, instead of
                truncating. `text-left` overrides the button's centred text. */}
            {isMulti ? (
              <span className="flex w-0 flex-1 items-center gap-1 truncate text-left">
                {multiSelectedLabels.length === 0 ? (
                  placeholder
                ) : multiSelectedLabels.length === 1 ? (
                  renderTriggerValue ? renderTriggerValue(multiSelectedLabels[0]!) : multiSelectedLabels[0]!.label
                ) : (
                  <>
                    {renderTriggerValue ? (
                      renderTriggerValue(multiSelectedLabels[0]!)
                    ) : (
                      <span className="truncate">{multiSelectedLabels[0]!.label}</span>
                    )}
                    <span className="text-muted-foreground shrink-0">
                      +{multiSelectedLabels.length - 1}
                    </span>
                  </>
                )}
              </span>
            ) : (
              <span
                className={cn(
                  "w-0 flex-1 text-left",
                  selectedOption && renderTriggerValue ? "flex items-center" : "truncate"
                )}
              >
                {selectedOption && renderTriggerValue
                  ? renderTriggerValue(selectedOption)
                  : (selectedLabel ?? placeholder)}
              </span>
            )}
            <ChevronDown className="ml-2 h-4 w-4 shrink-0" />
          </Button>
        </PopoverTrigger>
        <PopoverContent
          className={cn(
            "min-w-[--radix-popover-trigger-width] max-w-[--radix-popover-trigger-width] p-0",
            contentClassName
          )}
          align="start"
        >
          <div className="flex flex-col">
            {/* Selected items as chips (multi-select only) */}
            {/* `max-w-full` caps a chip at the popover's width, and the
                `min-w-0` flex wrapper lets the rendered label shrink inside
                it. Without both, a long name stretches its chip far past the
                popover and the remove icon with it, instead of truncating. */}
            {isMulti && multiSelectedLabels.length > 0 && (
              <div className="flex flex-wrap gap-1 border-b px-2 py-2">
                {multiSelectedLabels.map((opt) => (
                  <button
                    key={opt!.value}
                    type="button"
                    className="bg-muted hover:bg-muted/80 flex max-w-full items-center gap-1 rounded-xl p-1.5 text-xs transition-colors"
                    onClick={() => handleRemoveTag(opt!.value)}
                  >
                    {renderOption ? (
                      <span className="flex min-w-0">{renderOption(opt!)}</span>
                    ) : (
                      <span className="max-w-[120px] truncate">{opt!.label}</span>
                    )}
                    <X className="text-muted-foreground h-3 w-3 shrink-0" />
                  </button>
                ))}
              </div>
            )}

            {/* Search input */}
            <div className="shrink-0 p-2">
              <SenseySearchInput
                inputRef={searchInputRef}
                resetSignal={searchResetSignal}
                placeholder={searchPlaceholder}
                value={search}
                onSearchChange={(value) => setSearch(value ?? "")}
              />
            </div>

            {/* Options list */}
            <div
              className="max-h-60 overflow-y-auto overscroll-contain p-1"
              onWheel={(e) => e.stopPropagation()}
            >
              {showLoading ? (
                <div className="flex items-center justify-center py-4">
                  <Loader2 className="h-4 w-4 animate-spin" />
                </div>
              ) : filteredOptions.length === 0 && !showCreateButton ? (
                <div className="text-muted-foreground py-4 text-center text-sm">
                  {t("noResults")}
                </div>
              ) : optionSections ? (
                optionSections.map((section, index) => {
                  const headingId = `${groupIdPrefix}-group-${index}`;
                  return (
                    <div
                      key={section.group ?? ""}
                      role="group"
                      aria-labelledby={section.group ? headingId : undefined}
                    >
                      {section.group ? (
                        <div id={headingId} className={GROUP_HEADING_CLASS}>
                          {section.group}
                        </div>
                      ) : null}
                      {section.options.map(renderOptionButton)}
                    </div>
                  );
                })
              ) : (
                filteredOptions.map(renderOptionButton)
              )}

              {/* Create new option button */}
              {showCreateButton && !showLoading && (
                <>
                  {filteredOptions.length > 0 && (
                    <div className="bg-border my-1 h-px" />
                  )}
                  <button
                    type="button"
                    disabled={isCreating}
                    className={cn(
                      "text-primary hover:bg-accent flex w-full items-center gap-2 rounded-sm py-1.5 pl-2 pr-8 text-sm transition-colors outline-none select-none",
                      isCreating && "cursor-not-allowed opacity-50"
                    )}
                    onClick={handleCreate}
                  >
                    {isCreating ? (
                      <Loader2 className="h-4 w-4 shrink-0 animate-spin" />
                    ) : (
                      <Plus className="h-4 w-4 shrink-0" />
                    )}
                    <span className="truncate text-left">
                      {createLabel
                        ? createLabel(search.trim())
                        : `Create "${search.trim()}"`}
                    </span>
                  </button>
                </>
              )}
            </div>
          </div>
        </PopoverContent>
      </Popover>
    </div>
  );
}

// ── Form-integrated SelectField ─────────────────────────────────────

type SelectFieldBaseFP<TFieldValues extends FieldValues = FieldValues> =
  FormfieldProps<TFieldValues> &
    SelectFieldBaseProps;

type SelectFieldSingleProps<TFieldValues extends FieldValues = FieldValues> =
  SelectFieldBaseFP<TFieldValues> & {
    isMulti?: false;
  };

type SelectFieldMultiProps<TFieldValues extends FieldValues = FieldValues> =
  SelectFieldBaseFP<TFieldValues> & {
    isMulti: true;
  };

export type SelectFieldProps<TFieldValues extends FieldValues = FieldValues> =
  | SelectFieldSingleProps<TFieldValues>
  | SelectFieldMultiProps<TFieldValues>;

export function SelectField<TFieldValues extends FieldValues = FieldValues>(
  props: SelectFieldProps<TFieldValues>
) {
  const {
    name,
    label,
    description,
    control,
    required,
    tooltipText,
    placeholder,
    searchPlaceholder,
    options,
    setFilterParams,
    onSearchChange,
    renderOption,
    renderValue,
    isLoading,
    disabled,
    className,
    clearable,
    onCreate,
    createLabel,
    isCreating,
    orientation = "vertical",
    contentClassName,
  } = props;

  const isMulti = props.isMulti === true;
  const isHorizontal = orientation === "horizontal";

  return (
    <FormField
      control={control}
      name={name}
      render={({ field: { value, onChange } }) => (
        <FormItem className={cn(isHorizontal && HORIZONTAL_ROW_CLASS)}>
          {label && (
            <div className={cn("flex items-center gap-1 leading-5", isHorizontal && "min-w-0")}>
              <FormLabel
                tooltipText={tooltipText}
                className={cn(isHorizontal && HORIZONTAL_LABEL_CLASS)}
              >
                {label}
              </FormLabel>
              {required ? <span className="text-destructive text-sm">*</span> : null}
            </div>
          )}
          <FormControl>
            {isMulti ? (
              <SelectFieldStandalone
                isMulti
                value={(value as string[]) ?? []}
                onValueChange={onChange}
                options={options}
                placeholder={placeholder}
                searchPlaceholder={searchPlaceholder}
                setFilterParams={setFilterParams}
                onSearchChange={onSearchChange}
                renderOption={renderOption}
                renderValue={renderValue}
                isLoading={isLoading}
                disabled={disabled}
                className={className}
                onCreate={onCreate}
                createLabel={createLabel}
                isCreating={isCreating}
                contentClassName={contentClassName}
              />
            ) : (
              <SelectFieldStandalone
                value={(value as string) ?? ""}
                onValueChange={onChange}
                options={options}
                placeholder={placeholder}
                searchPlaceholder={searchPlaceholder}
                setFilterParams={setFilterParams}
                onSearchChange={onSearchChange}
                renderOption={renderOption}
                renderValue={renderValue}
                isLoading={isLoading}
                disabled={disabled}
                className={className}
                clearable={clearable}
                onCreate={onCreate}
                createLabel={createLabel}
                isCreating={isCreating}
                contentClassName={contentClassName}
              />
            )}
          </FormControl>
          {description ? (
            <FormDescription className={cn(isHorizontal && "col-start-2")}>
              {description}
            </FormDescription>
          ) : null}
          <FormMessage className={cn(isHorizontal && "col-start-2")} />
        </FormItem>
      )}
    />
  );
}
