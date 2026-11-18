import { AlertTriangle } from "lucide-react";

import { Button } from "./button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "./card";

/** A list's outline while its first answer loads. */
export function TableLoading({ label }: { label: string }) {
  return (
    <div className="flex flex-col gap-2" aria-busy="true" aria-label={label}>
      {Array.from({ length: 4 }, (_, index: number) => (
        <div key={index} className="bg-muted h-12 animate-pulse rounded-md" />
      ))}
    </div>
  );
}

/** A list or page whose data failed to load, with a way to ask again. */
export function LoadError({
  title,
  message,
  onRetry,
  retryLabel = "Try again",
}: {
  title: string;
  message: string;
  onRetry: () => void;
  retryLabel?: string;
}) {
  return (
    <Card role="alert" className="border-error/40 bg-error-light gap-4">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-lg">
          <AlertTriangle className="text-error size-5" aria-hidden />
          {title}
        </CardTitle>
        <CardDescription className="text-foreground">{message}</CardDescription>
      </CardHeader>
      <CardContent>
        <Button variant="outline" onClick={onRetry}>
          {retryLabel}
        </Button>
      </CardContent>
    </Card>
  );
}

/** A list with nothing in it yet. */
export function EmptyState({ title, description }: { title: string; description: string }) {
  return (
    <Card className="items-center py-10 text-center">
      <CardHeader className="w-full justify-items-center">
        <CardTitle className="text-lg">{title}</CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
    </Card>
  );
}
