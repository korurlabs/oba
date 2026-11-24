/**
 * All Loading State
 *
 * Skeleton for the combined All tab — sprint section + backlog section,
 * both rendered as table-like skeletons (the page is list-only).
 */

import { TableRowSkeleton } from '@/components/shared/loading-skeleton';

export default function AllLoading() {
  return (
    <div className="space-y-6 p-6">
      {/* Sprint section skeleton */}
      <div className="space-y-3">
        <div className="flex items-center gap-2">
          <div className="h-5 w-32 animate-pulse rounded-md bg-muted" />
          <div className="h-4 w-12 animate-pulse bg-muted" />
        </div>
        <div className="rounded-lg border border-border">
          <div className="border-b border-border p-4">
            <div className="flex gap-4">
              <div className="h-4 w-12 animate-pulse rounded-md bg-muted" />
              <div className="h-4 flex-1 animate-pulse rounded-md bg-muted" />
            </div>
          </div>
          <div className="divide-y divide-border p-4">
            {Array.from({ length: 4 }).map((_, i) => (
              <TableRowSkeleton key={i} />
            ))}
          </div>
        </div>
      </div>

      {/* Backlog section skeleton */}
      <div className="space-y-3">
        <div className="h-5 w-24 animate-pulse rounded-md bg-muted" />
        <div className="rounded-lg border border-border">
          <div className="border-b border-border p-4">
            <div className="flex gap-4">
              <div className="h-4 w-12 animate-pulse rounded-md bg-muted" />
              <div className="h-4 flex-1 animate-pulse rounded-md bg-muted" />
            </div>
          </div>
          <div className="divide-y divide-border p-4">
            {Array.from({ length: 8 }).map((_, i) => (
              <TableRowSkeleton key={i} />
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
