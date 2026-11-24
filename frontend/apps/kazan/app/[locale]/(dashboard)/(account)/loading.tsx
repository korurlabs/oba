/**
 * Account Route Group Loading State
 * Next.js automatically shows this while page.tsx is loading
 */
export default function AccountLoading() {
  return (
    <div className="border-border bg-card mx-auto min-h-full max-w-3xl space-y-6 border-x px-8 py-10">
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
            className="border border-border bg-card p-6 space-y-3"
          >
            <div className="flex items-center gap-4">
              <div className="h-10 w-10 animate-pulse bg-muted" />
              <div className="space-y-2">
                <div className="h-4 w-32 animate-pulse bg-muted" />
                <div className="h-3 w-48 animate-pulse bg-muted" />
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
