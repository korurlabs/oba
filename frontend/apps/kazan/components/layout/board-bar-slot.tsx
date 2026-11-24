"use client";

import { createContext, useContext, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";

type SlotSetter = (element: HTMLDivElement | null) => void;

interface BoardBarSlotValue {
  slot: HTMLDivElement | null;
  setSlot: SlotSetter;
}

const BoardBarSlotContext = createContext<BoardBarSlotValue | null>(null);

/**
 * Connects the board bar to the page under it. The search, filter and view
 * controls belong to the page (their state lives in its page hook), but they
 * are shown at the right end of the board bar so a board needs one row of
 * chrome less. The page keeps rendering them in its own tree and they are
 * drawn into the bar's slot, so no state is lifted or copied.
 */
export function BoardBarSlotProvider({ children }: { children: ReactNode }) {
  const [slot, setSlot] = useState<HTMLDivElement | null>(null);
  return (
    <BoardBarSlotContext.Provider value={{ slot, setSlot }}>
      {children}
    </BoardBarSlotContext.Provider>
  );
}

/** The element of the board bar that receives the page's controls. */
export function BoardBarSlotTarget({ className }: { className?: string }) {
  const context = useContext(BoardBarSlotContext);
  if (!context) {
    throw new Error("BoardBarSlotTarget must be rendered inside BoardBarSlotProvider");
  }
  return <div ref={context.setSlot} className={className} />;
}

/**
 * Renders the page's view controls in the board bar. Nothing is drawn until
 * the bar has mounted its slot, which happens in the same commit on the
 * client.
 */
export function BoardBarControls({ children }: { children: ReactNode }) {
  const context = useContext(BoardBarSlotContext);
  if (!context) {
    throw new Error("BoardBarControls must be rendered inside BoardBarSlotProvider");
  }
  return context.slot ? createPortal(children, context.slot) : null;
}
