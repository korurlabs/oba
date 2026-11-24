import type { ReactNode } from "react";

interface PageHeaderProps {
  title: string;
  description?: string;
  /** Buttons or filters that act on the whole page, set at the header's trailing edge. */
  children?: ReactNode;
}

/**
 * A Kazan page heading in the grid look: the title keeps its size and leads
 * with the grid's small square, and a dashed primary hairline closes the
 * header off from the page body.
 */
export function PageHeader({ title, description, children }: PageHeaderProps) {
  return (
    <header className="border-primary/25 flex flex-wrap items-end justify-between gap-3 border-b border-dashed pb-4">
      <div className="flex min-w-0 flex-col gap-1">
        <h1 className="text-foreground flex items-center gap-2.5 text-2xl font-semibold">
          <span aria-hidden className="bg-(--kazan-grid-mark) size-2 shrink-0" />
          <span className="min-w-0">{title}</span>
        </h1>
        {description && <p className="text-muted-foreground text-sm">{description}</p>}
      </div>
      {children}
    </header>
  );
}
