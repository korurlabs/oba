import Link from "next/link";

import { Button } from "@oba/ui/sky/button";

export default function NotFound() {
  return (
    <div className="flex flex-col items-start gap-4">
      <h1>Page not found</h1>
      <p className="text-muted-foreground">This page does not exist or has moved.</p>
      <Button asChild>
        <Link href="/">Back to the sessions</Link>
      </Button>
    </div>
  );
}
