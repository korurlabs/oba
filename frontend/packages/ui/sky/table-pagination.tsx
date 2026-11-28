import { ChevronLeft, ChevronRight } from "lucide-react";

import { Button } from "./button";

export type TablePaginationProps = {
  /** The page shown, counted from 1. */
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  /** Names the navigation for assistive technology, such as "Campaign pages". */
  label: string;
  previousLabel: string;
  nextLabel: string;
  /** Where the list stands, such as "Page 2 of 5 · 93 relations". */
  summary: string;
};

/** The pages of a list below it, as on Kaya's wall pages: back, where it stands, forward. */
export function TablePagination({
  page,
  totalPages,
  onPageChange,
  label,
  previousLabel,
  nextLabel,
  summary,
}: TablePaginationProps) {
  return (
    <nav
      data-slot="table-pagination"
      aria-label={label}
      className="flex items-center justify-between gap-4 text-sm"
    >
      <Button variant="outline" disabled={page <= 1} onClick={() => onPageChange(page - 1)}>
        <ChevronLeft className="size-4" aria-hidden />
        {previousLabel}
      </Button>
      <span className="text-muted-foreground tabular-nums">{summary}</span>
      <Button
        variant="outline"
        disabled={page >= totalPages}
        onClick={() => onPageChange(page + 1)}
      >
        {nextLabel}
        <ChevronRight className="size-4" aria-hidden />
      </Button>
    </nav>
  );
}
