import type { ComponentType, ReactNode } from "react";
import NextLink from "next/link";
import { ArrowLeft } from "lucide-react";

import type { SkyLinkProps } from "./app-shell";
import { Button } from "./button";

/** Where the back link above a heading leads, and its translated text, such as "All campaigns". */
export type PageHeadingBack = { href: string; label: string };

export type PageHeadingProps = {
  title: string;
  /**
   * A line on what the page holds. A string is set as muted text; a detail
   * page may pass its own row instead, such as badges and dates.
   */
  description?: ReactNode;
  /** The page's main actions, at the right of the title. */
  actions?: ReactNode;
  /** A link back to the list or page this one belongs to, above the title. */
  back?: PageHeadingBack;
  /** The link used by the back link; defaults to `next/link`. An app with locale routes passes its own. */
  linkComponent?: ComponentType<SkyLinkProps>;
};

/**
 * The top of a sky page: an optional back link, then the title with a line
 * on what the page holds, and the page's main actions at the right. The back
 * link is a small ghost button with an arrow, the style of every detail and
 * edit page in Otak and Kalkan, so each sky app leads back the same way.
 */
export function PageHeading({
  title,
  description,
  actions,
  back,
  linkComponent: Link = NextLink,
}: PageHeadingProps) {
  const heading = (
    <div data-slot="page-heading" className="flex flex-wrap items-end justify-between gap-4">
      <div className="flex min-w-0 flex-col gap-1">
        <h1 className="break-words">{title}</h1>
        {typeof description === "string"
          ? description !== "" && <p className="text-muted-foreground">{description}</p>
          : description}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
  if (!back) return heading;
  // The page's own column spaces its rows with gap-6, so the back link keeps that gap to the title.
  return (
    <div className="flex flex-col gap-6">
      <Button asChild variant="ghost" size="sm" className="w-fit">
        <Link href={back.href}>
          <ArrowLeft aria-hidden />
          {back.label}
        </Link>
      </Button>
      {heading}
    </div>
  );
}
