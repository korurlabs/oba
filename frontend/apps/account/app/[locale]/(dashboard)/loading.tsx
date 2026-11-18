/**
 * Dashboard Loading State
 * Next.js automatically shows this while page.tsx is loading
 */
export default function AccountLoading() {
  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6" aria-busy="true">
      {/* Heading skeleton */}
      <div className="flex flex-col gap-2">
        <div className="bg-muted h-8 w-48 animate-pulse rounded-md" />
        <div className="bg-muted h-4 w-96 max-w-full animate-pulse rounded-md" />
      </div>

      {/* Panel skeletons, shaped like the pages' white cards */}
      {Array.from({ length: 3 }).map((_, i) => (
        <div key={i} className="bg-card flex flex-col gap-3 rounded-xl border p-6 shadow-sm">
          <div className="bg-muted h-3 w-32 animate-pulse rounded-md" />
          <div className="bg-muted h-3 w-64 max-w-full animate-pulse rounded-md" />
        </div>
      ))}
    </div>
  );
}
