/**
 * The `@modal` parallel-route slot.
 *
 * The slot has no routes of its own any more: the ticket dialog is driven by
 * the `t_ticket` SEARCH PARAM on whatever list url the user is already on, not
 * by an intercepted route. That is what makes a refresh keep the user where
 * they were — an intercepting route only fires on soft navigations, so a hard
 * reload used to fall through to the standalone full page.
 *
 * `default.tsx` therefore matches every url under `(dashboard)` and mounts
 * `TicketModalHost`, which renders the dialog when the param is present and
 * nothing when it is not.
 *
 * The layout renders the slot inside the app access gate, next to the page, so
 * an organization without the Kaizen app sees no dialog. The dialog content
 * renders through a portal to the document body, so the scroll area and the
 * board layout's `overflow-hidden` wrapper do not clip it.
 */
import { Suspense } from "react";
import { TicketModalHost } from "@/features/tickets/components/ticket-modal-host";

export default function ModalSlotDefault() {
  // The host reads the url on the client. The Suspense boundary is what keeps
  // that from opting the whole slot out of prerendering if `(dashboard)` ever
  // stops being force-dynamic; `null` is the correct fallback either way,
  // since "no dialog" is the overwhelmingly common state.
  return (
    <Suspense fallback={null}>
      <TicketModalHost />
    </Suspense>
  );
}
