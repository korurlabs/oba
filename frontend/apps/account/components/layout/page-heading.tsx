import type { ReactNode } from "react";
import { Badge } from "@oba/ui/sky/badge";
import { Card, CardAction, CardDescription, CardHeader, CardTitle } from "@oba/ui/sky/card";

/**
 * The heading block of an Account page, as Kaya's pages have it: the title,
 * the line under it that says what the page is for, and an optional action at
 * the end of the row.
 */
export function PageHeading({
  title,
  description,
  action,
}: {
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-4">
      <div className="flex flex-col gap-1">
        <h1>{title}</h1>
        <p className="text-muted-foreground">{description}</p>
      </div>
      {action}
    </div>
  );
}

/**
 * The title of a panel. The card's own title is a plain block, so the heading
 * element sits inside it: the page outline keeps one level per panel, and `id`
 * names the panel for `aria-labelledby`.
 */
export function PanelTitle({ id, children }: { id: string; children: ReactNode }) {
  return (
    <CardTitle>
      <h2 id={id} className="text-lg font-semibold">
        {children}
      </h2>
    </CardTitle>
  );
}

/**
 * A panel for a section that is not built yet: what it will hold, and a
 * "coming soon" badge in place of a control that would do nothing. A panel
 * that is one of several on a page takes a title, which names it as a region;
 * a panel that is the whole page leaves the title out, so the page heading is
 * not repeated.
 */
export function ComingSoonPanel({
  id,
  title,
  description,
  body,
  comingSoon,
}: ({ id: string; title: string } | { id?: undefined; title?: undefined }) & {
  description: string;
  body: string;
  comingSoon: string;
}) {
  const named = title !== undefined && id !== undefined;
  return (
    <Card {...(named ? { "aria-labelledby": id, role: "region" } : {})}>
      <CardHeader>
        {named && <PanelTitle id={id}>{title}</PanelTitle>}
        <CardDescription>{description}</CardDescription>
        <CardAction>
          <Badge variant="muted">{comingSoon}</Badge>
        </CardAction>
      </CardHeader>
      <p className="text-muted-foreground px-6 text-sm">{body}</p>
    </Card>
  );
}
