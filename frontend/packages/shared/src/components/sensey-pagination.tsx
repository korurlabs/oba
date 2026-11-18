"use client";

import { Button } from "@oba/ui/components/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@oba/ui/components/select";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { cn } from "@oba/ui/lib/utils";

export type PaginationData = {
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
  first: boolean;
  last: boolean;
  empty: boolean;
};

type SenseyPaginationProps = {
  pagination: PaginationData | null | undefined;
  page: number;
  amount: number;
  onPageChange: (page: number) => void;
  onAmountChange: (amount: number) => void;
  pageSizeOptions?: number[];
  labels?: {
    results?: string;
    page?: string;
  };
  className?: string;
};

export function SenseyPagination({
  pagination,
  page,
  amount,
  onPageChange,
  onAmountChange,
  pageSizeOptions = [10, 25, 50],
  labels,
  className,
}: SenseyPaginationProps) {
  const totalElements = pagination?.totalElements ?? 0;
  const totalPages = pagination?.totalPages ?? 1;
  const isFirst = pagination?.first ?? true;
  const isLast = pagination?.last ?? true;

  return (
    <div className={cn("flex items-center gap-2", className)}>
      {/* Total results */}
      <span className="text-xs text-muted-foreground flex-1">
        {totalElements} {labels?.results ?? "results"}
      </span>

      {/* Navigation */}
      <div className="flex items-center gap-1 justify-center text-center">
        {/* Page size selector */}
        <Select
          value={String(amount)}
          onValueChange={(val) => {
            onAmountChange(Number(val));
            onPageChange(1);
          }}
        >
          <SelectTrigger size="sm" className="h-8 w-[70px] text-xs">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {pageSizeOptions.map((size) => (
              <SelectItem key={size} value={String(size)}>
                {size}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Button
          variant="ghost"
          size="icon"
          disabled={isFirst}
          onClick={() => onPageChange(Math.max(1, page - 1))}
          className="h-7 w-7"
        >
          <ChevronLeft className="h-3.5 w-3.5" />
        </Button>
        {/* Page indicator */}
        <span className="text-xs text-muted-foreground">
          {page}/{totalPages}
        </span>
        <Button
          variant="ghost"
          size="icon"
          disabled={isLast}
          onClick={() => onPageChange(page + 1)}
          className="h-7 w-7"
        >
          <ChevronRight className="h-3.5 w-3.5" />
        </Button>
      </div>
    </div>
  );
}
