/**
 * AllDropSection
 *
 * Registers one of the All tab's two sections as a droppable container so a
 * row can be dropped anywhere inside it -- including its header and its empty
 * state -- not only onto another row.
 *
 * The drop highlight is not drawn here: the section's table rings itself
 * (`isDropTarget` on SprintList and BacklogList), so the header and the
 * selection bar never light up while the whole section still accepts drops.
 */

"use client";

import type { ReactNode } from "react";
import { useDroppable } from "@dnd-kit/core";
import type { AllSectionContainerId } from "@/features/all/lib/all-section-dnd";

interface AllDropSectionProps {
  id: AllSectionContainerId;
  /**
   * Accessible name for the drop target. Paired with role="region" below --
   * aria-label on a bare div maps to the name-prohibited `generic` role and
   * assistive tech ignores it.
   */
  label: string;
  children: ReactNode;
}

export function AllDropSection({ id, label, children }: AllDropSectionProps) {
  const { setNodeRef } = useDroppable({ id });

  return (
    <div ref={setNodeRef} role="region" aria-label={label}>
      {children}
    </div>
  );
}
