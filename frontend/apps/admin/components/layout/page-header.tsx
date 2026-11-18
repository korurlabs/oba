import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

interface PageHeaderProps {
  title: ReactNode;
  description?: string;
  /** Controls on the right of the title, such as the page's main action. */
  actions?: ReactNode;
  className?: string;
}

/**
 * The heading block of an Admin page, as on Kaya's pages: the title, one
 * line on what the page is for, and the page's actions on the right, which
 * wrap below the title on narrow windows. The heading's size and weight come
 * from the sky design's `h1`.
 */
export function PageHeader({ title, description, actions, className }: PageHeaderProps) {
  return (
    <div className={cn("flex flex-wrap items-end justify-between gap-4", className)}>
      <div className="flex min-w-0 flex-col gap-1">
        <h1 className="break-words">{title}</h1>
        {description && <p className="text-muted-foreground">{description}</p>}
      </div>
      {actions && <div className="flex shrink-0 items-center gap-2">{actions}</div>}
    </div>
  );
}
