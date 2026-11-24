"use client";

import { Fragment, useState } from "react";
import type { ReactNode } from "react";
import { useTranslations } from "next-intl";
import { Filter, X } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { cn } from "@/lib/utils";

/** One field of a channel filter, listed in the left pane with the number of its chosen values. */
export interface ChatChannelFilterCategory {
  key: string;
  label: string;
  count: number;
}

interface ChatChannelFilterPopoverProps {
  /** The number of fields that narrow the channel, shown on the funnel. */
  activeCount: number;
  categories: ChatChannelFilterCategory[];
  /** The right pane of the chosen field; it fills a column whose height is fixed. */
  renderOptions: (key: string) => ReactNode;
  onClearAll: () => void;
}

/**
 * The funnel of a channel's filters: a button badged with the number of
 * active fields that opens a two-pane popup, the fields on the left and the
 * chosen field's options on the right. The channel's filter state lives in
 * the URL and is owned by the caller. The popup's content mounts only while
 * it is open, so an options pane that loads data sends no request until the
 * reader opens it.
 */
export function ChatChannelFilterPopover(props: ChatChannelFilterPopoverProps) {
  const tFilters = useTranslations("filters");
  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button type="button" variant="outline" size="sm" className="border-border h-8 shrink-0 gap-2 px-3">
          <Filter className="h-4 w-4" aria-hidden />
          <span className="sr-only">{tFilters("title")}</span>
          {props.activeCount > 0 && (
            <Badge variant="secondary" className="bg-primary/15 hover:bg-primary/15 text-primary ml-1">
              {props.activeCount}
            </Badge>
          )}
        </Button>
      </PopoverTrigger>
      <PopoverContent
        align="end"
        collisionPadding={16}
        className="flex max-h-[var(--radix-popover-content-available-height)] w-[min(520px,calc(100vw-2rem))] flex-col overflow-hidden p-0"
      >
        <ChatChannelFilterContent {...props} />
      </PopoverContent>
    </Popover>
  );
}

function ChatChannelFilterContent({ activeCount, categories, renderOptions, onClearAll }: ChatChannelFilterPopoverProps) {
  const tFilters = useTranslations("filters");
  // Which field the open popup shows: UI state of the popup, not a filter.
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  // Remounts the options pane on every click of a field, so a search typed there starts empty again.
  const [selectionCount, setSelectionCount] = useState(0);

  const handleCategorySelect = (key: string) => {
    setSelectedCategory(key);
    setSelectionCount((count) => count + 1);
  };

  return (
    <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
      {/* A fixed height, so the popup does not jump when the reader switches between fields. */}
      <div className="flex max-h-[320px] min-h-0 basis-80">
        <div className="w-[40%] overflow-y-auto border-r py-1">
          {categories.map((category) => {
            const isActive = selectedCategory === category.key;
            return (
              <button
                key={category.key}
                type="button"
                aria-pressed={isActive}
                onClick={() => handleCategorySelect(category.key)}
                className={cn(
                  "flex w-full items-center justify-between px-4 py-2.5 text-left text-sm transition-colors",
                  isActive
                    ? "bg-accent text-foreground border-primary border-l-2"
                    : "hover:bg-muted/50 border-l-2 border-transparent",
                )}
              >
                <span className="font-medium">{category.label}</span>
                {category.count > 0 && (
                  <span className="kazan-grid-number h-5 w-auto min-w-5 px-1">
                    {category.count}
                  </span>
                )}
              </button>
            );
          })}
        </div>

        <div className="flex min-h-0 w-[60%] min-w-0 flex-col">
          {selectedCategory === null ? (
            <div className="text-muted-foreground flex flex-1 items-center justify-center px-4 text-center text-sm">
              {tFilters("selectField")}
            </div>
          ) : (
            <Fragment key={selectionCount}>{renderOptions(selectedCategory)}</Fragment>
          )}
        </div>
      </div>

      {activeCount > 0 && (
        <div className="shrink-0 border-t px-4 py-2">
          <Button type="button" variant="ghost" size="sm" className="w-full gap-2" onClick={onClearAll}>
            <X className="h-4 w-4" />
            {tFilters("clearAll")}
          </Button>
        </div>
      )}
    </div>
  );
}
