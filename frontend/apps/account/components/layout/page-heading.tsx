import type { ReactNode } from "react";
import { Badge } from "@oba/ui/sky/badge";
import { Card, CardAction, CardDescription, CardHeader, CardTitle } from "@oba/ui/sky/card";

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
