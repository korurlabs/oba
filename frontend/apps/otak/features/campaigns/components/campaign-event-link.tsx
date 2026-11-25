"use client";

import { useTranslations } from "next-intl";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { useEvent } from "@/features/events/hooks/use-events";

/**
 * The linked event, as a link to its page. Until the event is read, or when
 * it cannot be read, the link says "Event" without a name, so the event page
 * explains what went wrong.
 */
export function CampaignEventLink({ eventId }: { eventId: string }) {
  const t = useTranslations("campaigns");
  const event = useEvent(eventId);
  const name = event.data?.data?.name;
  return (
    <Link
      href={`/events/${eventId}`}
      onClick={(clickEvent) => clickEvent.stopPropagation()}
      className="block truncate text-sm hover:underline"
    >
      {name ?? t("eventUnnamed")}
    </Link>
  );
}
