"use client";

import {
  useRef,
  useState,
  type KeyboardEvent as ReactKeyboardEvent,
  type PointerEvent as ReactPointerEvent,
} from "react";
import { useTranslations } from "next-intl";
import { useSidebar } from "@oba/ui/sky/sidebar";
import { cn } from "@/lib/utils";
import {
  clampSidebarWidth,
  SIDEBAR_WIDTH_KEY_STEP_PX,
  SIDEBAR_WIDTH_MAX_PX,
  SIDEBAR_WIDTH_MIN_PX,
} from "@/hooks/use-sidebar-width";
import { useSidebarWidthContext } from "./sidebar-context";

interface DragState {
  pointerId: number;
  startX: number;
  /** Rendered width of the sidebar when the drag began. */
  startWidth: number;
  /** Last width shown during the drag, remembered when the drag ends. */
  width: number;
}

/**
 * The drag-to-resize edge of the desktop menu panel, a window splitter
 * between the menu and the page. The app shell draws it in place of the
 * primitive's rail, inside the sidebar container whose width it measures.
 *
 * Dragging changes the width live between the minimum and the maximum, and
 * the width is remembered when the drag ends. The handle only resizes: the
 * menu stays expanded on a wide window, so a drag past the minimum holds at
 * the minimum and a click does nothing.
 *
 * The phone layout shows the sidebar as a sheet of fixed width, and the icon
 * mode has no width to choose, so there is no handle in either.
 */
export function SidebarResizeHandle() {
  const t = useTranslations("sidebar");
  const { open, isMobile } = useSidebar();
  const { width, preview, commit, setResizing } = useSidebarWidthContext();
  const dragRef = useRef<DragState | null>(null);
  const [dragging, setDragging] = useState(false);

  if (isMobile || !open) return null;

  const onPointerDown = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (event.button !== 0) return;
    event.preventDefault();
    const container = event.currentTarget.closest('[data-slot="sidebar-container"]');
    event.currentTarget.setPointerCapture(event.pointerId);
    dragRef.current = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startWidth: container ? container.getBoundingClientRect().width : width,
      width,
    };
    setDragging(true);
    setResizing(true);
  };

  const onPointerMove = (event: ReactPointerEvent<HTMLDivElement>) => {
    const drag = dragRef.current;
    if (drag?.pointerId !== event.pointerId) return;
    drag.width = clampSidebarWidth(drag.startWidth + event.clientX - drag.startX);
    preview(drag.width);
  };

  const endDrag = (event: ReactPointerEvent<HTMLDivElement>) => {
    const drag = dragRef.current;
    if (drag?.pointerId !== event.pointerId) return;
    commit(drag.width);
    dragRef.current = null;
    setDragging(false);
    setResizing(false);
  };

  const onKeyDown = (event: ReactKeyboardEvent<HTMLDivElement>) => {
    switch (event.key) {
      case "ArrowLeft":
        commit(width - SIDEBAR_WIDTH_KEY_STEP_PX);
        break;
      case "ArrowRight":
        commit(width + SIDEBAR_WIDTH_KEY_STEP_PX);
        break;
      case "Home":
        commit(SIDEBAR_WIDTH_MIN_PX);
        break;
      case "End":
        commit(SIDEBAR_WIDTH_MAX_PX);
        break;
      default:
        return;
    }
    event.preventDefault();
  };

  return (
    <div
      role="separator"
      aria-orientation="vertical"
      aria-label={t("resizeSidebar")}
      aria-valuemin={SIDEBAR_WIDTH_MIN_PX}
      aria-valuemax={SIDEBAR_WIDTH_MAX_PX}
      aria-valuenow={width}
      tabIndex={0}
      title={t("resizeSidebar")}
      data-sidebar="rail"
      data-resizing={dragging}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={endDrag}
      onPointerCancel={endDrag}
      onKeyDown={onKeyDown}
      className={cn(
        "absolute inset-y-0 z-20 flex w-4 -translate-x-1/2 cursor-col-resize touch-none group-data-[side=left]:-right-4",
        "hover:after:bg-sidebar-border data-[resizing=true]:after:bg-sidebar-border after:absolute after:inset-y-0 after:left-1/2 after:w-[2px]",
        "focus-visible:after:bg-primary focus-visible:outline-none"
      )}
    />
  );
}
