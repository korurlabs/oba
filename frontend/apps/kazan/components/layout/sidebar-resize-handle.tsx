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
  SIDEBAR_COLLAPSE_THRESHOLD_PX,
  SIDEBAR_WIDTH_KEY_STEP_PX,
  SIDEBAR_WIDTH_MAX_PX,
  SIDEBAR_WIDTH_MIN_PX,
} from "@/hooks/use-sidebar-width";
import { useSidebarWidthContext } from "./sidebar-context";

interface DragState {
  pointerId: number;
  startX: number;
  /** Rendered width of the sidebar when the drag began; the icon rail's width when collapsed. */
  startWidth: number;
  /** Expanded width when the drag began, restored if the drag ends collapsed. */
  widthBeforeDrag: number;
  /** Whether the drag currently has the sidebar expanded. */
  open: boolean;
  /** Last width shown during the drag, remembered when the drag ends expanded. */
  width: number;
}

/**
 * The drag-to-resize edge of the desktop menu panel, a window splitter
 * between the menu and the page. The app shell draws it in place of the
 * primitive's rail, inside the sidebar container whose width it measures.
 *
 * Dragging changes the width live between the minimum and the maximum. Below
 * the minimum the sidebar holds at the minimum until the pointer passes the
 * collapse threshold, where it switches to the icon mode; moving back past the
 * threshold in the same drag expands it again. A drag that ends collapsed
 * keeps the width remembered before it, so expanding returns to that width.
 * A click does nothing: collapsing is the top-bar toggle's job.
 *
 * The phone layout shows the sidebar as a sheet of fixed width, so there is
 * no rail there.
 */
export function SidebarResizeHandle() {
  const t = useTranslations("sidebar");
  const { open, setOpen, isMobile } = useSidebar();
  const { width, preview, commit, setResizing } = useSidebarWidthContext();
  const dragRef = useRef<DragState | null>(null);
  const [dragging, setDragging] = useState(false);

  if (isMobile) return null;

  const onPointerDown = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (event.button !== 0) return;
    event.preventDefault();
    const container = event.currentTarget.closest('[data-slot="sidebar-container"]');
    event.currentTarget.setPointerCapture(event.pointerId);
    dragRef.current = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startWidth: container ? container.getBoundingClientRect().width : width,
      widthBeforeDrag: width,
      open,
      width,
    };
    setDragging(true);
    setResizing(true);
  };

  const onPointerMove = (event: ReactPointerEvent<HTMLDivElement>) => {
    const drag = dragRef.current;
    if (drag?.pointerId !== event.pointerId) return;
    const raw = drag.startWidth + event.clientX - drag.startX;
    const shouldBeOpen = raw >= SIDEBAR_COLLAPSE_THRESHOLD_PX;
    if (shouldBeOpen !== drag.open) {
      drag.open = shouldBeOpen;
      setOpen(shouldBeOpen);
    }
    if (drag.open) {
      drag.width = clampSidebarWidth(raw);
      preview(drag.width);
    }
  };

  const endDrag = (event: ReactPointerEvent<HTMLDivElement>) => {
    const drag = dragRef.current;
    if (drag?.pointerId !== event.pointerId) return;
    if (drag.open) commit(drag.width);
    else preview(drag.widthBeforeDrag);
    dragRef.current = null;
    setDragging(false);
    setResizing(false);
  };

  const onKeyDown = (event: ReactKeyboardEvent<HTMLDivElement>) => {
    switch (event.key) {
      case "ArrowLeft":
        if (!open) return;
        if (width <= SIDEBAR_WIDTH_MIN_PX) setOpen(false);
        else commit(width - SIDEBAR_WIDTH_KEY_STEP_PX);
        break;
      case "ArrowRight":
        if (open) commit(width + SIDEBAR_WIDTH_KEY_STEP_PX);
        else setOpen(true);
        break;
      case "Home":
        commit(SIDEBAR_WIDTH_MIN_PX);
        if (!open) setOpen(true);
        break;
      case "End":
        commit(SIDEBAR_WIDTH_MAX_PX);
        if (!open) setOpen(true);
        break;
      case "Enter":
        setOpen(!open);
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
      aria-valuemin={0}
      aria-valuemax={SIDEBAR_WIDTH_MAX_PX}
      aria-valuenow={open ? width : 0}
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
        "after:absolute after:inset-y-0 after:left-1/2 after:w-[2px] hover:after:bg-sidebar-border data-[resizing=true]:after:bg-sidebar-border",
        "focus-visible:outline-none focus-visible:after:bg-primary",
      )}
    />
  );
}
