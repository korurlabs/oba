/**
 * Dashboard Loading State
 * Next.js shows this while a page of the shell loads.
 */
export default function OtakLoading() {
  return (
    <div className="flex flex-col gap-6">
      <div className="space-y-2">
        <div className="bg-muted h-8 w-48 animate-pulse rounded-md" />
        <div className="bg-muted h-4 w-96 max-w-full animate-pulse rounded-md" />
      </div>
      <div className="border-border bg-card space-y-3 rounded-xl border p-6 shadow-sm">
        {Array.from({ length: 5 }).map((_, i) => (
          <div key={i} className="bg-muted h-6 w-full animate-pulse rounded" />
        ))}
      </div>
    </div>
  );
}
