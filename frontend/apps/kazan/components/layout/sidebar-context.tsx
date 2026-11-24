'use client';

import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type CSSProperties,
  type ReactNode,
} from 'react';
import { useSidebar } from '@oba/ui/sky/sidebar';
import { cn } from '@/lib/utils';
import { useSidebarWidth } from '@/hooks/use-sidebar-width';

interface SidebarWidthContextValue {
  /** Width of the expanded desktop sidebar in px. */
  width: number;
  /** Changes the width without remembering it. */
  preview: (width: number) => void;
  /** Changes the width and remembers it for this browser. */
  commit: (width: number) => void;
  setResizing: (resizing: boolean) => void;
  /** Style for the sidebar provider: the chosen width as `--sidebar-width`. */
  providerStyle: CSSProperties;
  /** Classes for the sidebar provider: no width animation and a resize cursor while needed. */
  providerClassName: string;
}

const SidebarWidthContext = createContext<SidebarWidthContextValue | null>(null);

/**
 * The sidebar width the viewer chooses by dragging the sidebar's edge. It
 * sits above the app shell, which passes `providerStyle` and
 * `providerClassName` to its sidebar provider: the width reaches the
 * primitive through `--sidebar-width`, so the icon mode and the mobile sheet
 * keep their own widths.
 *
 * The primitive animates width changes. While the edge is dragged the
 * animation is switched off, so the sidebar and the content panel follow the
 * pointer instead of trailing it. It is also off until the stored width has
 * been painted once, so a reload does not animate from the default width to
 * the stored one.
 */
export function SidebarWidthProvider({ children }: { children: ReactNode }) {
  const { width, isRestored, preview, commit } = useSidebarWidth();
  const [isResizing, setResizing] = useState(false);
  const [isSettled, setIsSettled] = useState(false);

  useEffect(() => {
    if (!isRestored) return;
    // Two frames: the first paints the restored width, the second turns the
    // animation back on after that paint.
    let secondFrame = 0;
    const firstFrame = window.requestAnimationFrame(() => {
      secondFrame = window.requestAnimationFrame(() => setIsSettled(true));
    });
    return () => {
      window.cancelAnimationFrame(firstFrame);
      window.cancelAnimationFrame(secondFrame);
    };
  }, [isRestored]);

  const value = useMemo<SidebarWidthContextValue>(
    () => ({
      width,
      preview,
      commit,
      setResizing,
      providerStyle: { '--sidebar-width': `${width}px` } as CSSProperties,
      providerClassName: cn(
        (isResizing || !isSettled) &&
          '[&_[data-slot=sidebar-container]]:transition-none [&_[data-slot=sidebar-gap]]:transition-none',
        isResizing && 'cursor-col-resize select-none',
      ),
    }),
    [width, preview, commit, isResizing, isSettled],
  );

  return <SidebarWidthContext.Provider value={value}>{children}</SidebarWidthContext.Provider>;
}

export function useSidebarWidthContext(): SidebarWidthContextValue {
  const context = useContext(SidebarWidthContext);
  if (!context) {
    throw new Error('useSidebarWidthContext must be used within a SidebarWidthProvider.');
  }
  return context;
}

/**
 * Adapter hook that wraps the sky sidebar's useSidebar() into the shape the
 * Kazan menu rows and the board tree expect:
 *   mobileOpen, openMobile(), closeMobile(), setMobileOpen()
 */
export function useSidebarContext() {
  const ctx = useSidebar();

  return {
    mobileOpen: ctx.openMobile,
    openMobile: () => ctx.setOpenMobile(true),
    closeMobile: () => ctx.setOpenMobile(false),
    setMobileOpen: ctx.setOpenMobile,
    /** Expose the full shadcn context for components that need it */
    state: ctx.state,
    open: ctx.open,
    setOpen: ctx.setOpen,
    isMobile: ctx.isMobile,
    toggleSidebar: ctx.toggleSidebar,
  };
}
