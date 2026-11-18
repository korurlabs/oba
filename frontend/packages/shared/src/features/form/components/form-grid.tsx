import { cn } from "@oba/ui/lib/utils";

export function FormGrid({
  children,
  className,
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("grid grid-cols-1 items-start gap-x-6 gap-y-4 [&>*]:min-w-0 md:grid-cols-2", className)}>
      {children}
    </div>
  );
}
