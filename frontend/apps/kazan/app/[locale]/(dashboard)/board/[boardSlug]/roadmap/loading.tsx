/**
 * Roadmap Loading State
 */

import { Skeleton } from "@/components/ui/skeleton";

export default function RoadmapLoading() {
  return (
    <div className="flex h-full flex-col">
      <div className="border-border flex items-center gap-2 border-b px-4 py-2 lg:px-6">
        <Skeleton className="h-5 w-24" />
        <div className="flex-1" />
        <Skeleton className="h-8 w-36" />
        <Skeleton className="h-8 w-32" />
      </div>
      <div className="space-y-3 p-4 lg:p-6">
        <Skeleton className="h-10 w-full" />
        {Array.from({ length: 3 }).map((_, i) => (
          <Skeleton key={i} className="h-20 w-full" />
        ))}
      </div>
    </div>
  );
}
