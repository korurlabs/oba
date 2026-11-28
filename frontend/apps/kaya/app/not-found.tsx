import Link from "next/link";

import { Button } from "@oba/ui/sky/button";
import { PageHeading } from "@oba/ui/sky/page-heading";

export default function NotFound() {
  return (
    <div className="flex flex-col items-start gap-4">
      <PageHeading title="Page not found" description="This page does not exist or has moved." />
      <Button asChild>
        <Link href="/">Back to the sessions</Link>
      </Button>
    </div>
  );
}
