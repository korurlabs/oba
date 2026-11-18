"use client";

import type { LucideIcon } from "lucide-react";
import { AlertCircle, ExternalLink, Link2, Siren, Ticket } from "lucide-react";
import { useTranslations } from "next-intl";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Skeleton } from "@/components/ui/skeleton";
import { KORUR_APPS } from "@/constants/korur-apps";
import { useHomeSummary } from "../hooks/use-home-summary";

const ITEM_CLASS = "cursor-pointer gap-2";

interface CountProps {
  value: number | undefined;
  isPending: boolean;
  unavailableLabel: string;
}

/**
 * The live count beside a Kazan link. A failed fetch shows a warning icon
 * rather than 0, so the user never reads an untrusted count as "nothing open".
 */
function LinkCount({ value, isPending, unavailableLabel }: CountProps) {
  if (isPending) return <Skeleton className="ml-auto h-4 w-6" />;
  if (value === undefined) {
    return (
      <span className="text-muted-foreground ml-auto" title={unavailableLabel}>
        <AlertCircle aria-hidden className="size-4" />
        <span className="sr-only">{unavailableLabel}</span>
      </span>
    );
  }
  return <span className="text-muted-foreground ml-auto text-xs tabular-nums">{value}</span>;
}

interface KazanLink {
  href: string;
  icon: LucideIcon;
  label: string;
  count: number | undefined;
}

/**
 * Every link the home page offers, behind one icon in the header, so the page
 * body is left for the sprint and the work. The Kazan group carries the open
 * incident and open sprint ticket counts; the Korur apps other than Kazan
 * open in a new tab so the user keeps their place here.
 */
export function HomeLinksMenu() {
  const t = useTranslations("home");
  const { data, isPending, isError } = useHomeSummary();
  const summary = isError ? undefined : data?.data;

  const kazanLinks: KazanLink[] = [
    {
      href: "/incidents",
      icon: Siren,
      label: t("links.openIncidents"),
      count: summary?.openIncidents,
    },
    {
      href: "/tickets",
      icon: Ticket,
      label: t("links.openSprintTickets"),
      count: summary?.openSprintTickets,
    },
  ];

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button type="button" variant="outline" size="icon" aria-label={t("links.label")}>
          <Link2 aria-hidden className="size-4" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-64">
        <DropdownMenuGroup>
          <DropdownMenuLabel>{t("links.kazan")}</DropdownMenuLabel>
          {kazanLinks.map(({ href, icon: Icon, label, count }) => (
            <DropdownMenuItem key={href} asChild className={ITEM_CLASS}>
              <Link href={href}>
                <Icon aria-hidden className="text-muted-foreground size-4" />
                <span>{label}</span>
                <LinkCount
                  value={count}
                  isPending={isPending}
                  unavailableLabel={t("links.countUnavailable")}
                />
              </Link>
            </DropdownMenuItem>
          ))}
        </DropdownMenuGroup>
        <DropdownMenuSeparator />
        <DropdownMenuGroup>
          <DropdownMenuLabel>{t("links.apps")}</DropdownMenuLabel>
          {KORUR_APPS.map((app) => {
            const Icon = app.icon;
            const name = t(`apps.${app.id}.name`);
            return (
              <DropdownMenuItem key={app.id} asChild className={ITEM_CLASS}>
                {app.external ? (
                  <a href={app.href} target="_blank" rel="noopener noreferrer">
                    <Icon aria-hidden className="text-muted-foreground size-4" />
                    <span>{name}</span>
                    <ExternalLink aria-hidden className="text-muted-foreground ml-auto size-3.5" />
                    <span className="sr-only">{t("opensInNewTab")}</span>
                  </a>
                ) : (
                  <Link href={app.href}>
                    <Icon aria-hidden className="text-muted-foreground size-4" />
                    <span>{name}</span>
                  </Link>
                )}
              </DropdownMenuItem>
            );
          })}
        </DropdownMenuGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
