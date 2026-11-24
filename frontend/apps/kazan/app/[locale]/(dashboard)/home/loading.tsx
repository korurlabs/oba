import { Skeleton } from "@/components/ui/skeleton";

const SECTION_CLASS = "kazan-grid-frame bg-card flex flex-col gap-4 p-4 sm:p-5";

function ListSectionSkeleton({ rows }: { rows: number }) {
  return (
    <div className={SECTION_CLASS}>
      <Skeleton className="h-5 w-40" />
      <div className="flex flex-col gap-2">
        {Array.from({ length: rows }, (_, index) => (
          <Skeleton key={index} className="h-9 w-full" />
        ))}
      </div>
    </div>
  );
}

/**
 * Shown while the home page prefetches. It has the shape of the dashboard
 * (header, sprint strip, leaderboard beside deadlines, stale backlog), so the
 * page does not jump when the real sections replace it.
 */
export default function HomeLoading() {
  return (
    <div aria-busy className="h-full overflow-auto">
      <div className="mx-auto flex w-full max-w-6xl flex-col gap-6 px-4 py-6 sm:px-6 sm:py-8">
        <div className="flex items-center justify-between gap-4">
          <Skeleton className="h-8 w-40" />
          <Skeleton className="size-9 rounded-md" />
        </div>

        <div className="kazan-grid-frame bg-card flex flex-col gap-4 p-4 sm:p-5 md:flex-row">
          <div className="flex flex-1 flex-col gap-3">
            <Skeleton className="h-4 w-32" />
            <Skeleton className="h-9 w-40" />
            <Skeleton className="h-2 w-full" />
          </div>
          <Skeleton className="h-44 flex-[2]" />
        </div>

        <div className="grid grid-cols-1 items-start gap-6 lg:grid-cols-2">
          <ListSectionSkeleton rows={5} />
          <ListSectionSkeleton rows={5} />
        </div>

        <ListSectionSkeleton rows={5} />
      </div>
    </div>
  );
}
