/**
 * Dashboard Loading State
 * Next.js automatically shows this while page.tsx is loading
 */
export default function DashboardLoading() {
  return (
    <div className="mx-auto max-w-3xl space-y-6 px-8 py-10">
      {/* Header skeleton */}
      <div className="space-y-2">
        <div className="bg-muted h-8 w-48 animate-pulse rounded-md" />
        <div className="bg-muted h-4 w-96 animate-pulse rounded-md" />
      </div>

      {/* Card skeletons */}
      <div className="mt-8 space-y-6">
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className="border-border bg-card space-y-3 rounded-2xl border p-6">
            <div className="flex items-center gap-4">
              <div className="bg-muted h-10 w-10 animate-pulse rounded-xl" />
              <div className="space-y-2">
                <div className="bg-muted h-4 w-32 animate-pulse rounded" />
                <div className="bg-muted h-3 w-48 animate-pulse rounded" />
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
