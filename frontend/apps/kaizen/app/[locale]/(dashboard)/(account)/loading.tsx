/**
 * Account Route Group Loading State
 * Next.js automatically shows this while page.tsx is loading
 */
export default function AccountLoading() {
  return (
    <div className="mx-auto max-w-3xl px-8 py-10 space-y-6">
      {/* Header skeleton */}
      <div className="space-y-2">
        <div className="h-8 w-48 animate-pulse rounded-md bg-muted" />
        <div className="h-4 w-96 animate-pulse rounded-md bg-muted" />
      </div>

      {/* Card skeletons */}
      <div className="space-y-6 mt-8">
        {Array.from({ length: 3 }).map((_, i) => (
          <div
            key={i}
            className="rounded-2xl border border-border bg-card p-6 space-y-3"
          >
            <div className="flex items-center gap-4">
              <div className="h-10 w-10 animate-pulse rounded-xl bg-muted" />
              <div className="space-y-2">
                <div className="h-4 w-32 animate-pulse rounded bg-muted" />
                <div className="h-3 w-48 animate-pulse rounded bg-muted" />
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
