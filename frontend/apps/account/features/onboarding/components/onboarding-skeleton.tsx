/** Loading card of the onboarding page: the Suspense fallback and the stepper's initial load. */
export function OnboardingSkeleton() {
  return (
    <div className="bg-card mx-auto w-full max-w-4xl animate-pulse rounded-3xl p-8 shadow-xl sm:p-12">
      <div className="mb-6 flex justify-center">
        <div className="bg-muted h-[72px] w-[72px] rounded-[20px]" />
      </div>
      <div className="mb-8 text-center">
        <div className="bg-muted mx-auto mb-2 h-8 w-48 rounded" />
        <div className="bg-muted mx-auto h-5 w-64 rounded" />
      </div>
      <div className="space-y-4">
        <div className="bg-muted h-10 rounded" />
        <div className="bg-muted h-10 rounded" />
        <div className="bg-muted h-12 rounded-xl" />
      </div>
    </div>
  );
}
