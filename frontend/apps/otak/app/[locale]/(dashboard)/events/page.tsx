import { EventsView } from "@/features/events/components/events-view";

/**
 * Event List Page
 *
 * Not server-prefetched: the Upcoming and Past tabs are judged on the
 * viewer's calendar day, which only the browser knows, so the view fetches
 * its page on mount. The dashboard layout gates auth.
 */
export default function EventsPage() {
  return <EventsView />;
}
